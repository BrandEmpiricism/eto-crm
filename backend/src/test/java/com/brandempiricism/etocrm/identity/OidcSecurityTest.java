package com.brandempiricism.etocrm.identity;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "eto.security.mode=oidc")
@AutoConfigureMockMvc
class OidcSecurityTest {
    private static final UUID TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("platformJdbcTemplate") JdbcTemplate database;
    @MockitoBean JwtDecoder decoder;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean IdentityApplicationApi identities;
    @Autowired @Qualifier("tenantJdbcTemplate") JdbcTemplate tenantDatabase;

    @BeforeEach
    void activeMembership() {
        database.update("update tenant_registry set status = 'ACTIVE' where id = ?", TENANT_ID);
        database.update("insert into platform_identity(id, created_at) values (?, ?)", "oidc-user", Instant.now());
        database.update("insert into tenant_membership(identity_id, tenant_id, role, status, created_at, updated_at) values (?, ?, 'BUSINESS_DEVELOPMENT', 'ACTIVE', ?, ?)",
            "oidc-user", TENANT_ID, Instant.now(), Instant.now());
    }

    @AfterEach
    void restoreFixture() {
        database.update("delete from identity_audit_record where actor_id = ?", "oidc-user");
        database.update("delete from tenant_membership where identity_id = ?", "oidc-user");
        database.update("delete from platform_identity where id = ?", "oidc-user");
        database.update("update tenant_registry set status = 'PROVISIONING' where id = ?", TENANT_ID);
    }

    @Test
    void verifiedSubjectAndKnownRoleAuthorizeAWrite() throws Exception {
        var now = Instant.now();
        when(decoder.decode("valid-token")).thenReturn(Jwt.withTokenValue("valid-token")
            .header("alg", "RS256").subject("oidc-user").issuedAt(now).expiresAt(now.plusSeconds(300))
            .claim("roles", List.of("BUSINESS_DEVELOPMENT")).build());

        mvc.perform(post("/api/accounts")
                .header("Authorization", "Bearer valid-token")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"OIDC Tools","industry":"Manufacturing","location":"Ontario"}
                    """))
            .andExpect(status().isCreated());
    }

    @Test
    void invalidBearerTokenReturnsSafeProblemDetails() throws Exception {
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("signature detail must not escape"));

        mvc.perform(post("/api/accounts")
                .header("Authorization", "Bearer invalid-token")
                .header("X-Request-Id", "oidc-failure")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Authentication failed"))
            .andExpect(jsonPath("$.detail").value("A valid bearer token is required."))
            .andExpect(jsonPath("$.requestId").value("oidc-failure"));
    }

    @Test
    void actorHeaderCannotAuthenticateInOidcMode() throws Exception {
        mvc.perform(post("/api/accounts").header("X-Actor", "forged-user")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutRequestedMembershipFailsClosed() throws Exception {
        stubValidToken();
        var unrelatedTenant = UUID.randomUUID();

        mvc.perform(post("/api/accounts")
                .header("Authorization", "Bearer valid-token")
                .header("X-Tenant-Id", unrelatedTenant)
                .header("X-Request-Id", "tenant-denied")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Tenant access denied"))
            .andExpect(jsonPath("$.requestId").value("tenant-denied"));
    }

    @Test
    void disabledMembershipCannotReachTenantApplicationServices() throws Exception {
        stubValidToken();
        database.update("update tenant_membership set status = 'DISABLED' where identity_id = ? and tenant_id = ?",
            "oidc-user", TENANT_ID);

        mvc.perform(post("/api/accounts")
                .header("Authorization", "Bearer valid-token")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("An active company membership is required."));
    }

    @Test
    void signedTokenFailuresAreCorrelatedAndNeverLogSensitiveClaimsOrRequestContent() throws Exception {
        var keys = keys();
        var realDecoder = org.springframework.security.oauth2.jwt.NimbusJwtDecoder
            .withPublicKey((java.security.interfaces.RSAPublicKey) keys.getPublic()).build();
        realDecoder.setJwtValidator(OidcTokenConfiguration.validator("https://issuer.example.test", "eto-crm"));
        when(decoder.decode(anyString())).thenAnswer(call -> realDecoder.decode(call.getArgument(0, String.class)));
        var invalid = new java.util.ArrayList<String>();
        for (var claims : new com.nimbusds.jwt.JWTClaimsSet.Builder[] {
                claims().issuer("https://untrusted.example.test"), claims().audience("another-app"),
                claims().audience((String) null), claims().expirationTime(null),
                claims().expirationTime(java.util.Date.from(Instant.now().minusSeconds(300))),
                claims().subject(null), claims().subject(" ")}) invalid.add(token(keys, claims));
        invalid.add(token(keys(), claims()));
        try (var logs = new com.brandempiricism.etocrm.commons.observability.LogCapture()) {
            for (int i = 0; i < invalid.size(); i++) {
                var requestId = "signed-rejection-" + i;
                mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + invalid.get(i))
                        .header("X-Tenant-Id", TENANT_ID).header("X-Request-Id", requestId)
                        .header("Cookie", "session=cookie-canary").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"customer-content-canary\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("A valid bearer token is required."))
                    .andExpect(jsonPath("$.requestId").value(requestId));
            }
            assertThat(logs.events("security.authentication.rejected")).hasSize(invalid.size())
                .allSatisfy(event -> {
                    assertThat(event.path("requestId").asText()).startsWith("signed-rejection-");
                    assertThat(event.path("traceId").asText()).hasSize(32);
                    assertThat(event.has("actorId")).isFalse();
                    assertThat(event.has("tenantId")).isFalse();
                });
            assertThat(logs.output()).doesNotContain("canary", "Authorization", "Cookie");
            invalid.forEach(value -> assertThat(logs.output()).doesNotContain(value));
        }
    }

    @Test
    void signedSubjectOwnsWriteAndTokenClaimsCannotOverrideMembershipOrRoute() throws Exception {
        var keys = keys();
        var realDecoder = org.springframework.security.oauth2.jwt.NimbusJwtDecoder
            .withPublicKey((java.security.interfaces.RSAPublicKey) keys.getPublic()).build();
        realDecoder.setJwtValidator(OidcTokenConfiguration.validator("https://issuer.example.test", "eto-crm"));
        when(decoder.decode(anyString())).thenAnswer(call -> realDecoder.decode(call.getArgument(0, String.class)));
        var signed = token(keys, claims().claim("roles", java.util.Map.of("malformed", "PLATFORM_OPERATOR"))
            .claim("tenantId", UUID.randomUUID().toString()).claim("database", "jdbc:postgresql://host-canary/db"));
        try (var logs = new com.brandempiricism.etocrm.commons.observability.LogCapture()) {
            var result = mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + signed)
                    .header("X-Actor", "forged-user").header("X-Tenant-Id", TENANT_ID)
                    .header("X-Request-Id", "signed-create").header("X-Business-Transaction-Id", "signed-workflow")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"customer-content-canary\",\"industry\":\"Manufacturing\",\"location\":\"Ontario\"}"))
                .andExpect(status().isCreated()).andReturn();
            var id = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString()).path("id").asText());
            assertThat(tenantDatabase.queryForObject("select created_by from account where id = ?", String.class, id)).isEqualTo("oidc-user");
            var event = logs.events("account.created").getFirst();
            assertThat(event.path("actorId").asText()).isEqualTo("oidc-user");
            assertThat(event.path("tenantId").asText()).isEqualTo(TENANT_ID.toString());
            assertThat(event.path("businessTransactionId").asText()).isEqualTo("signed-workflow");
            assertThat(logs.output()).doesNotContain("canary", signed, "forged-user");
        }
    }

    @Test
    void membershipStorageFailureReturnsCorrelatedSafeProblemBeforeTenantDataAccess() throws Exception {
        stubValidToken();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("jdbc:postgresql://host-canary/db password-canary"))
            .when(org.springframework.test.util.AopTestUtils.<IdentityApplicationApi>getUltimateTargetObject(identities))
            .selectTenant("oidc-user", TENANT_ID);
        try (var logs = new com.brandempiricism.etocrm.commons.observability.LogCapture()) {
            mvc.perform(post("/api/accounts").header("Authorization", "Bearer valid-token")
                    .header("X-Tenant-Id", TENANT_ID).header("X-Request-Id", "membership-unavailable")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Identity verification is temporarily unavailable."))
                .andExpect(jsonPath("$.requestId").value("membership-unavailable"));
            var event = logs.events("security.membership.unavailable").getFirst();
            assertThat(event.path("actorId").asText()).isEqualTo("oidc-user");
            assertThat(event.path("requestId").asText()).isEqualTo("membership-unavailable");
            assertThat(event.has("tenantId")).isFalse();
            assertThat(logs.events("account.created")).isEmpty();
            assertThat(logs.output()).doesNotContain("canary", "jdbc:");
        }
        assertThat(TenantContextHolder.current()).isEmpty();
    }

    @Test
    void configuredConsoleEncoderStaysSafeAtTraceLevel() {
        var root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var console = root.getAppender("CONSOLE");
        assertThat(console).isInstanceOf(ch.qos.logback.core.OutputStreamAppender.class);
        assertThat(((ch.qos.logback.core.OutputStreamAppender<?>) console).getEncoder())
            .isInstanceOf(com.brandempiricism.etocrm.commons.observability.SafeLogEncoder.class);
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("sensitive.level.test");
        var previous = logger.getLevel();
        try (var logs = new com.brandempiricism.etocrm.commons.observability.LogCapture()) {
            logger.setLevel(ch.qos.logback.classic.Level.TRACE);
            logger.trace("Authorization: Bearer token-canary");
            logger.debug("body={}", "customer-canary");
            assertThat(logs.events("framework.diagnostic")).hasSize(2);
            assertThat(logs.output()).doesNotContain("canary", "Authorization");
        } finally { logger.setLevel(previous); }
    }

    private static com.nimbusds.jwt.JWTClaimsSet.Builder claims() {
        return new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("https://issuer.example.test").audience("eto-crm")
            .subject("oidc-user").issueTime(java.util.Date.from(Instant.now()))
            .expirationTime(java.util.Date.from(Instant.now().plusSeconds(300)))
            .claim("email", "personal-canary@example.test").claim("privateClaim", "claim-canary");
    }
    private static String token(java.security.KeyPair keys, com.nimbusds.jwt.JWTClaimsSet.Builder claims) throws Exception {
        var jwt = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256), claims.build());
        jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner((java.security.interfaces.RSAPrivateKey) keys.getPrivate()));
        return jwt.serialize();
    }
    private static java.security.KeyPair keys() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private void stubValidToken() {
        var now = Instant.now();
        when(decoder.decode("valid-token")).thenReturn(Jwt.withTokenValue("valid-token")
            .header("alg", "RS256").subject("oidc-user").issuedAt(now).expiresAt(now.plusSeconds(300))
            .claim("roles", List.of("BUSINESS_DEVELOPMENT")).build());
    }
}
