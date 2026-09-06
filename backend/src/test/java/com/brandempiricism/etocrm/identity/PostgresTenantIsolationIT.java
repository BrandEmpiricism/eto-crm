package com.brandempiricism.etocrm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brandempiricism.etocrm.platform.tenancy.TenantDatabaseCredentialProvider;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Runs only with -Ppostgres-acceptance against an explicitly configured disposable PostgreSQL server. */
@SpringBootTest(properties = {
    "eto.security.mode=oidc", "eto.tenancy.routing.enabled=true",
    "spring.sql.init.mode=never",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureMockMvc
class PostgresTenantIsolationIT {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID SHARED_RECORD = UUID.randomUUID();
    private static final String PREFIX = "acceptance_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PLATFORM = PREFIX + "_platform";
    private static final String DATABASE_A = PREFIX + "_a";
    private static final String DATABASE_B = PREFIX + "_b";
    private static final String ADMIN_URL = required("TEST_POSTGRES_URL");
    private static final String ADMIN_USER = required("TEST_POSTGRES_USERNAME");
    private static final String ADMIN_PASSWORD = required("TEST_POSTGRES_PASSWORD");
    private static final String TENANT_PASSWORD = UUID.randomUUID().toString();

    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired @Qualifier("platformJdbcTemplate") JdbcTemplate platform;
    @Autowired IdentityApplicationApi identities;
    @Autowired TenantJobExecutor jobs;
    @Autowired com.brandempiricism.etocrm.accounts.AccountApplicationApi accounts;
    @Autowired com.brandempiricism.etocrm.platform.tenancy.TenantDatabaseRoutingApi routing;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean TenantDatabaseCredentialProvider credentials;

    @DynamicPropertySource
    static void databases(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             var sql = connection.createStatement()) {
            sql.execute("create database " + PLATFORM);
            for (var name : List.of(DATABASE_A, DATABASE_B)) {
                sql.execute("create role " + name + " login password '" + TENANT_PASSWORD + "'");
                sql.execute("create database " + name + " owner " + name);
                sql.execute("revoke connect on database " + name + " from public");
                sql.execute("grant connect on database " + name + " to " + name);
                Flyway.configure().dataSource(url(name), name, TENANT_PASSWORD)
                    .locations("classpath:db/tenant").load().migrate();
                tenant(name).update("insert into account(id,name,industry,location,created_at,created_by) values (?,?, 'Manufacturing','Ontario',?, 'fixture')",
                    SHARED_RECORD, name.equals(DATABASE_A) ? "Tenant A account" : "Tenant B account", java.sql.Timestamp.from(Instant.now()));
            }
        }
        properties.add("eto.platform.datasource.url", () -> url(PLATFORM));
        properties.add("eto.platform.datasource.username", () -> ADMIN_USER);
        properties.add("eto.platform.datasource.password", () -> ADMIN_PASSWORD);
        properties.add("eto.tenancy.routing.database-server-url", () -> ADMIN_URL);
    }

    @BeforeEach
    void membershipsAndTokens() {
        for (var entry : List.of(new Tenant(A, DATABASE_A, "user-a"), new Tenant(B, DATABASE_B, "user-b"))) {
            platform.update("insert into tenant_registry(id,slug,display_name,database_name,credential_secret_ref,status,provisioning_step,idempotency_key,created_at,created_by,updated_at,updated_by) "
                + "values (?,?,?,?,?,'ACTIVE','COMPLETE',?,?, 'fixture',?, 'fixture') on conflict(id) do update set status='ACTIVE'",
                entry.id(), entry.name(), entry.name(), entry.name(), entry.name(), entry.name(), java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now()));
            platform.update("insert into platform_identity(id,created_at) values (?,?) on conflict do nothing", entry.user(), java.sql.Timestamp.from(Instant.now()));
            platform.update("insert into tenant_membership(identity_id,tenant_id,role,status,created_at,updated_at) values (?,?,'TENANT_ADMIN','ACTIVE',?,?) "
                + "on conflict(identity_id,tenant_id) do update set status='ACTIVE'", entry.user(), entry.id(), java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now()));
            when(credentials.resolve(entry.name())).thenReturn(new TenantDatabaseCredentialProvider.Credentials(entry.name(), TENANT_PASSWORD));
            var now = Instant.now();
            when(decoder.decode(entry.user())).thenReturn(Jwt.withTokenValue(entry.user()).header("alg", "RS256")
                .subject(entry.user()).issuedAt(now).expiresAt(now.plusSeconds(300)).build());
        }
    }

    @Test
    void sameRecordIdentifierResolvesOnlyWithinSelectedTenantEvenConcurrently() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var requests = executor.invokeAll(List.of(
                () -> mvc.perform(get("/api/accounts/" + SHARED_RECORD).header("Authorization", "Bearer user-a").header("X-Tenant-Id", A))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Tenant A account")),
                () -> mvc.perform(get("/api/accounts/" + SHARED_RECORD).header("Authorization", "Bearer user-b").header("X-Tenant-Id", B))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Tenant B account"))));
            for (var result : requests) result.get();
        }
    }

    @Test
    void writesReachOnlyAuthorizedTenantDatabase() throws Exception {
        mvc.perform(post("/api/accounts").header("Authorization", "Bearer user-a").header("X-Tenant-Id", A)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"A-only new prospect","industry":"Manufacturing","location":"Ontario"}
                """)).andExpect(status().isCreated());
        assertThat(tenant(DATABASE_A).queryForObject("select count(*) from account where name='A-only new prospect'", Integer.class)).isEqualTo(1);
        assertThat(tenant(DATABASE_B).queryForObject("select count(*) from account where name='A-only new prospect'", Integer.class)).isZero();
    }

    @Test
    void unauthorizedTenantAndRevokedMembershipFailBeforeCachedPoolAccess() throws Exception {
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer user-a").header("X-Tenant-Id", A)).andExpect(status().isOk());
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer user-a").header("X-Tenant-Id", B)).andExpect(status().isForbidden());
        platform.update("update tenant_membership set status='DISABLED' where identity_id='user-a'");
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer user-a").header("X-Tenant-Id", A)).andExpect(status().isForbidden());
    }

    @Test
    void suspensionBlocksNewRequestsAndUnrelatedTenantRemainsAvailable() throws Exception {
        platform.update("update tenant_registry set status='SUSPENDED' where id=?", A);
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer user-a").header("X-Tenant-Id", A)).andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer user-b").header("X-Tenant-Id", B)).andExpect(status().isOk());
    }

    @Test
    void schemasAndDatabaseCredentialsAreSeparated() {
        assertThat(platform.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='account'", Integer.class)).isZero();
        for (var name : List.of(DATABASE_A, DATABASE_B)) {
            assertThat(tenant(name).queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='tenant_registry'", Integer.class)).isZero();
        }
        assertThatThrownBy(() -> DriverManager.getConnection(url(DATABASE_B), DATABASE_A, TENANT_PASSWORD))
            .isInstanceOf(java.sql.SQLException.class);
    }

    @Test
    void memberOfTwoCompaniesSelectsExactlyOneTenantPerRequest() throws Exception {
        platform.update("insert into tenant_membership(identity_id,tenant_id,role,status,created_at,updated_at) "
            + "values ('user-a',?,'BUSINESS_DEVELOPMENT','ACTIVE',current_timestamp,current_timestamp)", B);
        try {
            mvc.perform(get("/api/accounts/" + SHARED_RECORD).header("Authorization", "Bearer user-a").header("X-Tenant-Id", B))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Tenant B account"));
            mvc.perform(get("/api/accounts/" + SHARED_RECORD).header("Authorization", "Bearer user-a").header("X-Tenant-Id", A))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Tenant A account"));
            assertThat(platform.queryForObject("select count(distinct tenant_id) from identity_audit_record "
                + "where actor_id='user-a' and action='tenant_membership.tenant_selected'", Integer.class)).isEqualTo(2);
        } finally {
            platform.update("delete from tenant_membership where identity_id='user-a' and tenant_id=?", B);
        }
    }

    @Test
    void authorizedServiceJobUsesItsTenantAndCannotImpersonateAnotherService() {
        platform.update("insert into platform_identity(id,created_at) values ('service:acceptance',current_timestamp)");
        platform.update("insert into tenant_membership(identity_id,tenant_id,role,status,created_at,updated_at) "
            + "values ('service:acceptance',?,'BUSINESS_DEVELOPMENT','ACTIVE',current_timestamp,current_timestamp)", A);
        var security = org.springframework.security.core.context.SecurityContextHolder.getContext();
        security.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "service:acceptance", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(Permissions.CRM_READ))));
        try {
            jobs.run("service:acceptance", A, () -> assertThat(accounts.getAccount(SHARED_RECORD).name()).isEqualTo("Tenant A account"));
            assertThat(TenantContextHolder.current()).isEmpty();
            assertThatThrownBy(() -> jobs.run("service:acceptance", B, () -> accounts.getAccount(SHARED_RECORD)))
                .isInstanceOf(TenantAccessDeniedException.class);
            assertThatThrownBy(() -> jobs.run("service:another", A, () -> accounts.getAccount(SHARED_RECORD)))
                .isInstanceOf(TenantAccessDeniedException.class);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            platform.update("delete from tenant_membership where identity_id='service:acceptance'");
            platform.update("delete from platform_identity where id='service:acceptance'");
        }
    }

    @Test
    void incompatibleSchemaIsRejectedBeforePoolCanServeBusinessQueries() {
        var database = tenant(DATABASE_A);
        var checksum = database.queryForObject("select checksum from flyway_schema_history where version='1'", Integer.class);
        database.update("update flyway_schema_history set checksum=0 where version='1'");
        var security = org.springframework.security.core.context.SecurityContextHolder.getContext();
        security.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user-a", null, List.of()));
        try {
            TenantContextHolder.bind(identities.selectTenant("user-a", A));
            var factory = new com.brandempiricism.etocrm.platform.tenancy.TenantPoolFactory(routing, credentials, ADMIN_URL, 2);
            assertThatThrownBy(() -> factory.create(A)).isInstanceOf(com.brandempiricism.etocrm.commons.ServiceUnavailableException.class)
                .hasMessage("Tenant database readiness or schema compatibility verification failed.");
        } finally {
            TenantContextHolder.clear();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            database.update("update flyway_schema_history set checksum=? where version='1'", checksum);
        }
    }

    @Test
    void unauthenticatedReadsAndMissingOrInvalidTenantSelectionFailClosed() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer user-a")).andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer user-a").header("X-Tenant-Id", "jdbc:postgresql://caller/database"))
            .andExpect(status().isForbidden());
    }

    @Test
    void contactsSignalsMatchesAndActionsCannotBeReadOrChangedFromAnotherTenantOrAccount() throws Exception {
        String root = "/api/accounts/" + SHARED_RECORD;
        var contact = create(root + "/contacts", """
            {"name":"Private contact","email":"private@example.test"}
            """);
        var signal = create(root + "/signals", """
            {"source":"Permit","observedOn":"2026-09-01","observedFact":"An assembly line is approved."}
            """);
        var match = create(root + "/capability-matches", """
            {"signalId":"%s","capabilityId":"11111111-1111-1111-1111-111111111111",
             "owner":"user-a","hypothesis":"Fixtures reduce changeovers.","nextAction":"Validate","nextActionDate":"2026-10-01"}
            """.formatted(signal));
        var action = create(root + "/next-actions", """
            {"capabilityMatchId":"%s","description":"Private follow-up","dueAt":"2099-10-01T00:00:00Z"}
            """.formatted(match));
        var otherAccount = create("/api/accounts", """
            {"name":"Another account","industry":"Manufacturing","location":"Ontario"}
            """);

        for (String resource : List.of("contacts/" + contact, "signals/" + signal, "capability-matches/" + match)) {
            mvc.perform(get(root + "/" + resource).header("Authorization", "Bearer user-a").header("X-Tenant-Id", A))
                .andExpect(status().isOk());
            notFound(get(root + "/" + resource), "user-b", B);
            notFound(get("/api/accounts/" + otherAccount + "/" + resource), "user-a", A);
            notFound(get(root + "/" + resource.substring(0, resource.indexOf('/')) + "/" + UUID.randomUUID()), "user-a", A);
        }
        for (var target : List.of(new Tenant(B, SHARED_RECORD.toString(), "user-b"), new Tenant(A, otherAccount, "user-a"))) {
            String targetRoot = "/api/accounts/" + target.name();
            notFound(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(targetRoot + "/contacts/" + contact)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Overwritten\",\"email\":\"bad@example.test\"}"), target.user(), target.id());
            notFound(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(targetRoot + "/next-actions/" + action + "/complete"), target.user(), target.id());
            notFound(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(targetRoot + "/next-actions/" + action + "/reschedule")
                .contentType(MediaType.APPLICATION_JSON).content("{\"dueAt\":\"2099-12-01T00:00:00Z\"}"), target.user(), target.id());
            notFound(post(targetRoot + "/capability-matches").contentType(MediaType.APPLICATION_JSON)
                .content("{\"signalId\":\"" + signal + "\",\"capabilityId\":\"11111111-1111-1111-1111-111111111111\"}"), target.user(), target.id());
            notFound(post(targetRoot + "/next-actions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"capabilityMatchId\":\"" + match + "\",\"description\":\"Stolen match\",\"dueAt\":\"2099-12-01T00:00:00Z\"}"), target.user(), target.id());
        }
        var a = tenant(DATABASE_A);
        assertThat(a.queryForObject("select name from account_contact where id=?", String.class, UUID.fromString(contact))).isEqualTo("Private contact");
        assertThat(a.queryForObject("select completed_at from next_action where id=?", java.sql.Timestamp.class, UUID.fromString(action))).isNull();
        assertThat(a.queryForObject("select due_at from next_action where id=?", java.sql.Timestamp.class, UUID.fromString(action)).toInstant())
            .isEqualTo(Instant.parse("2099-10-01T00:00:00Z"));
        for (var table : List.of("account_contact", "prospect_signal", "capability_match", "next_action")) {
            assertThat(tenant(DATABASE_B).queryForObject("select count(*) from " + table, Integer.class)).isZero();
        }
    }

    @Test
    void duplicateTenantSelectionFailsClosed() throws Exception {
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer user-a").header("X-Tenant-Id", A, B))
            .andExpect(status().isForbidden());
    }

    @Test
    void capabilityCatalogueAndOwnerQueueAreTenantScoped() throws Exception {
        var capability = UUID.randomUUID();
        tenant(DATABASE_A).update("insert into capability(id,name,description,active) values (?,?,?,true)",
            capability, "Tenant A private capability", "Private catalogue entry");
        var ownCatalogue = mvc.perform(get("/api/capabilities").header("Authorization", "Bearer user-a").header("X-Tenant-Id", A))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var otherCatalogue = mvc.perform(get("/api/capabilities").header("Authorization", "Bearer user-b").header("X-Tenant-Id", B))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ownCatalogue).contains(capability.toString());
        assertThat(otherCatalogue).doesNotContain(capability.toString(), "Tenant A private capability");
        mvc.perform(get("/api/prospecting/work-queue").param("owner", "user-a")
            .header("Authorization", "Bearer user-b").header("X-Tenant-Id", B))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void applicationReadRequiresContextMatchingAuthenticatedIdentityBeforeOpeningDatabase() {
        var security = org.springframework.security.core.context.SecurityContextHolder.getContext();
        security.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user-a", null,
            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(Permissions.CRM_READ))));
        org.mockito.Mockito.clearInvocations(credentials);
        try {
            assertThatThrownBy(() -> accounts.getAccount(SHARED_RECORD)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            TenantContextHolder.bind(new IdentityApplicationApi.TenantContext("user-b", B, IdentityApplicationApi.CompanyRole.TENANT_ADMIN));
            assertThatThrownBy(() -> accounts.getAccount(SHARED_RECORD)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            org.mockito.Mockito.verifyNoInteractions(credentials);
        } finally {
            TenantContextHolder.clear();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void supportIdentityCannotUseMembershipToEscalateIntoCrmAccess() {
        platform.update("insert into platform_identity(id,created_at) values ('service:support',current_timestamp)");
        platform.update("insert into tenant_membership(identity_id,tenant_id,role,status,created_at,updated_at) "
            + "values ('service:support',?,'BUSINESS_DEVELOPMENT','ACTIVE',current_timestamp,current_timestamp)", A);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("service:support", null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(Permissions.SUPPORT_DIAGNOSE))));
        try {
            assertThatThrownBy(() -> jobs.run("service:support", A, () -> accounts.getAccount(SHARED_RECORD)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThat(platform.queryForObject("select count(*) from identity_audit_record where actor_id='service:support' "
                + "and tenant_id=? and action='tenant_job.context_authorized'", Integer.class, A)).isEqualTo(1);
            assertThat(TenantContextHolder.current()).isEmpty();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            platform.update("delete from tenant_membership where identity_id='service:support'");
            platform.update("delete from platform_identity where id='service:support'");
        }
    }

    private String create(String path, String body) throws Exception {
        var response = mvc.perform(post(path).header("Authorization", "Bearer user-a").header("X-Tenant-Id", A)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("id").asText();
    }

    private void notFound(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String user, UUID tenant) throws Exception {
        mvc.perform(request.header("Authorization", "Bearer " + user).header("X-Tenant-Id", tenant).header("X-Request-Id", "ownership-check"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Resource not found"))
            .andExpect(jsonPath("$.detail").value("The requested resource was not found."))
            .andExpect(jsonPath("$.requestId").value("ownership-check"));
    }

    private static JdbcTemplate tenant(String name) {
        return new JdbcTemplate(new DriverManagerDataSource(url(name), name, TENANT_PASSWORD));
    }

    private static String url(String name) { return ADMIN_URL.substring(0, ADMIN_URL.lastIndexOf('/') + 1) + name; }
    private static String required(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must identify a disposable acceptance database server.");
        return value;
    }
    private record Tenant(UUID id, String name, String user) {}
}
