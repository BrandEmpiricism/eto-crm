package com.brandempiricism.etocrm.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brandempiricism.etocrm.commons.observability.LogCapture;
import com.brandempiricism.etocrm.identity.IdentityApplicationApi;
import com.brandempiricism.etocrm.identity.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:journal_tenant;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "eto.platform.datasource.url=jdbc:h2:mem:journal_platform;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class TenantEventJournalTest {
    private static final UUID DEV_TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String ACTIVE = "11111111-1111-1111-1111-111111111111";
    private static final String RETIRED = "22222222-2222-2222-2222-222222222222";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BusinessEventPublisher publisher;
    @Autowired @Qualifier("tenantJdbcTemplate") JdbcTemplate tenant;
    @Autowired @Qualifier("platformJdbcTemplate") JdbcTemplate platform;
    @Autowired @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTransactions;
    @Autowired @Qualifier("platformTransactionManager") PlatformTransactionManager platformTransactions;

    @AfterEach void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        org.slf4j.MDC.clear();
    }

    @Test void completeAndDraftSavesRecordMinimalVersionedCorrelatedFacts() throws Exception {
        for (boolean complete : new boolean[] {true, false}) {
            String workflow = UUID.randomUUID().toString();
            var before = Instant.now();
            var response = mvc.perform(post("/api/prospecting/matches").header("X-Actor", "journal-user")
                    .header("X-Tenant-Id", UUID.randomUUID())
                    .header("X-Request-Id", workflow).header("X-Business-Transaction-Id", workflow)
                    .header("traceparent", "00-1234567890abcdef1234567890abcdef-1234567890abcdef-00")
                    .contentType(MediaType.APPLICATION_JSON).content(walking(workflow, ACTIVE, complete)))
                .andExpect(status().isOk()).andReturn().getResponse();
            var match = json.readTree(response.getContentAsString());
            var events = events(workflow);
            assertThat(events).hasSize(3).extracting(Event::type)
                .containsExactlyInAnyOrder("account.created", "signal.recorded", "capability_match.saved");
            assertThat(events).extracting(Event::id).doesNotHaveDuplicates();
            for (var event : events) {
                assertThat(event.tenantId()).isEqualTo(DEV_TENANT);
                assertThat(event.version()).isEqualTo(1);
                assertThat(event.actor()).isEqualTo("journal-user");
                assertThat(event.request()).isEqualTo(workflow);
                assertThat(event.workflow()).isEqualTo(workflow);
                assertThat(event.trace()).isEqualTo("1234567890abcdef1234567890abcdef");
                assertThat(event.traceparent()).isEqualTo(response.getHeader("traceparent"));
                assertThat(event.occurredAt()).isBetween(before, Instant.now());
                assertThat(event.payload().toString()).doesNotContain("canary", "journal-user", "jdbc:");
                switch (event.type()) {
                    case "account.created" -> {
                        assertThat(event.aggregateType()).isEqualTo("ACCOUNT");
                        assertThat(event.aggregateId().toString()).isEqualTo(match.path("accountId").asText());
                        assertThat(event.payload().isObject()).isTrue();
                        assertThat(event.payload().size()).isZero();
                    }
                    case "signal.recorded" -> {
                        assertThat(event.aggregateType()).isEqualTo("SIGNAL");
                        assertThat(event.aggregateId().toString()).isEqualTo(match.path("signalId").asText());
                        assertThat(event.payload().size()).isEqualTo(1);
                        assertThat(event.payload().path("accountId")).isEqualTo(match.path("accountId"));
                    }
                    case "capability_match.saved" -> {
                        assertThat(event.aggregateType()).isEqualTo("CAPABILITY_MATCH");
                        assertThat(event.aggregateId().toString()).isEqualTo(match.path("id").asText());
                        assertThat(event.payload().size()).isEqualTo(4);
                        assertThat(event.payload().path("accountId")).isEqualTo(match.path("accountId"));
                        assertThat(event.payload().path("signalId")).isEqualTo(match.path("signalId"));
                        assertThat(event.payload().path("capabilityId").asText()).isEqualTo(ACTIVE);
                        assertThat(event.payload().path("status").asText()).isEqualTo(complete ? "ACTIVE" : "DRAFT");
                    }
                    default -> throw new AssertionError("Unexpected fact type");
                }
            }
        }
    }

    @Test void rejectedRetiredCapabilityLeavesNoPartialBusinessOrJournalRows() throws Exception {
        String workflow = UUID.randomUUID().toString();
        mvc.perform(post("/api/prospecting/matches").header("X-Actor", "journal-user")
                .header("X-Business-Transaction-Id", workflow).contentType(MediaType.APPLICATION_JSON)
                .content(walking(workflow, RETIRED, true)))
            .andExpect(status().isUnprocessableEntity());
        assertThat(events(workflow)).isEmpty();
        assertThat(tenant.queryForObject("select count(*) from account where name=?", Integer.class, workflow)).isZero();
        assertThat(tenant.queryForObject("select count(*) from capability_match where account_name=?", Integer.class, workflow)).isZero();
    }

    @Test void standaloneAccountSignalAndMatchEachRecordExactlyTheirOwnFact() throws Exception {
        String accountRequest = UUID.randomUUID().toString();
        var account = createAccount(accountRequest);
        String signalRequest = UUID.randomUUID().toString();
        var signal = createSignal(account, signalRequest);
        String matchRequest = UUID.randomUUID().toString();
        var result = mvc.perform(post("/api/accounts/{id}/capability-matches", account).header("X-Actor", "journal-user")
                .header("X-Business-Transaction-Id", matchRequest).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("signalId", signal, "capabilityId", ACTIVE))))
            .andExpect(status().isCreated()).andReturn();
        var match = json.readTree(result.getResponse().getContentAsString());
        assertThat(events(accountRequest)).hasSize(1).extracting(Event::type).containsExactly("account.created");
        assertThat(events(signalRequest)).hasSize(1).extracting(Event::type).containsExactly("signal.recorded");
        assertThat(events(matchRequest)).hasSize(1).extracting(Event::type).containsExactly("capability_match.saved");
        assertThat(events(matchRequest).getFirst().aggregateId().toString()).isEqualTo(match.path("id").asText());
    }

    @Test void failedJournalInsertRollsBackStandaloneAccount() throws Exception {
        String workflow = UUID.randomUUID().toString();
        reject("account.created", workflow);
        try (var logs = new LogCapture()) {
            mvc.perform(post("/api/accounts").header("X-Actor", "journal-user")
                    .header("X-Business-Transaction-Id", workflow).contentType(MediaType.APPLICATION_JSON).content(accountBody(workflow)))
                .andExpect(status().isInternalServerError());
            assertThat(events(workflow)).isEmpty();
            assertThat(tenant.queryForObject("select count(*) from account where name=?", Integer.class, workflow)).isZero();
            assertThat(logs.events("account.created")).isEmpty();
        } finally { allowInserts(); }
    }

    @Test void failedJournalInsertRollsBackStandaloneSignal() throws Exception {
        var account = createAccount(UUID.randomUUID().toString());
        String workflow = UUID.randomUUID().toString();
        reject("signal.recorded", workflow);
        try {
            mvc.perform(post("/api/accounts/{id}/signals", account).header("X-Actor", "journal-user")
                    .header("X-Business-Transaction-Id", workflow).contentType(MediaType.APPLICATION_JSON).content(signalBody()))
                .andExpect(status().isInternalServerError());
            assertThat(events(workflow)).isEmpty();
            assertThat(tenant.queryForObject("select count(*) from prospect_signal where account_id=?", Integer.class, account)).isZero();
        } finally { allowInserts(); }
    }

    @Test void failedFinalFactRollsBackEarlierWalkingSliceFactsAndBusinessRows() throws Exception {
        String workflow = UUID.randomUUID().toString();
        reject("capability_match.saved", workflow);
        try (var logs = new LogCapture()) {
            mvc.perform(post("/api/prospecting/matches").header("X-Actor", "journal-user")
                    .header("X-Business-Transaction-Id", workflow).contentType(MediaType.APPLICATION_JSON)
                    .content(walking(workflow, ACTIVE, true)))
                .andExpect(status().isInternalServerError());
            assertThat(events(workflow)).isEmpty();
            assertThat(tenant.queryForObject("select count(*) from account where name=?", Integer.class, workflow)).isZero();
            for (var type : List.of("account.created", "signal.recorded", "capability_match.saved")) assertThat(logs.events(type)).isEmpty();
        } finally { allowInserts(); }
    }

    @Test void journalDoesNotExistInPlatformDatabase() {
        assertThat(platform.queryForObject("select count(*) from information_schema.tables where table_name='TENANT_EVENT_JOURNAL'", Integer.class)).isZero();
    }

    @Test void failedJournalInsertRollsBackStandaloneMatchWithoutRemovingExistingAccountAndSignal() throws Exception {
        var account = createAccount(UUID.randomUUID().toString());
        var signal = createSignal(account, UUID.randomUUID().toString());
        String workflow = UUID.randomUUID().toString();
        reject("capability_match.saved", workflow);
        try {
            mvc.perform(post("/api/accounts/{id}/capability-matches", account).header("X-Actor", "journal-user")
                    .header("X-Business-Transaction-Id", workflow).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(java.util.Map.of("signalId", signal, "capabilityId", ACTIVE))))
                .andExpect(status().isInternalServerError());
            assertThat(events(workflow)).isEmpty();
            assertThat(tenant.queryForObject("select count(*) from capability_match where account_id=?", Integer.class, account)).isZero();
            assertThat(tenant.queryForObject("select count(*) from account where id=?", Integer.class, account)).isEqualTo(1);
            assertThat(tenant.queryForObject("select count(*) from prospect_signal where id=?", Integer.class, signal)).isEqualTo(1);
        } finally { allowInserts(); }
    }

    @Test void publisherRequiresAnExistingWritableTenantTransaction() {
        authenticate("journal-user", "crm:write");
        var event = new BusinessEvent.AccountCreated(UUID.randomUUID());
        assertThatThrownBy(() -> publisher.record(event, "journal-user")).isInstanceOf(IllegalTransactionStateException.class);
        var platformTransaction = new TransactionTemplate(platformTransactions);
        assertThatThrownBy(() -> platformTransaction.executeWithoutResult(tx -> publisher.record(event, "journal-user")))
            .isInstanceOf(IllegalTransactionStateException.class);
        var readOnly = new TransactionTemplate(tenantTransactions);
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.executeWithoutResult(tx -> publisher.record(event, "journal-user")))
            .isInstanceOf(IllegalStateException.class).hasMessage("Business events require a writable tenant transaction.");
        assertThat(tenant.queryForObject("select count(*) from tenant_event_journal where aggregate_id=?", Integer.class, event.accountId())).isZero();
    }

    @Test void publisherRejectsMissingPermissionForgedActorAndMismatchedTenantIdentity() {
        var event = new BusinessEvent.AccountCreated(UUID.randomUUID());
        var transaction = new TransactionTemplate(tenantTransactions);
        assertThatThrownBy(() -> publisher.record(event, "journal-user")).isInstanceOf(AccessDeniedException.class);
        authenticate("support-user", "support:diagnose");
        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> publisher.record(event, "support-user")))
            .isInstanceOf(AccessDeniedException.class);
        authenticate("journal-user", "crm:write");
        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> publisher.record(event, "forged-user")))
            .isInstanceOf(AccessDeniedException.class);
        TenantContextHolder.bind(new IdentityApplicationApi.TenantContext("other-user", UUID.randomUUID(), IdentityApplicationApi.CompanyRole.BUSINESS_DEVELOPMENT));
        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> publisher.record(event, "journal-user")))
            .isInstanceOf(AccessDeniedException.class);
        assertThat(tenant.queryForObject("select count(*) from tenant_event_journal where aggregate_id=?", Integer.class, event.accountId())).isZero();
    }

    private UUID createAccount(String workflow) throws Exception {
        var result = mvc.perform(post("/api/accounts").header("X-Actor", "journal-user")
                .header("X-Business-Transaction-Id", workflow).contentType(MediaType.APPLICATION_JSON).content(accountBody(workflow)))
            .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }
    private UUID createSignal(UUID account, String workflow) throws Exception {
        var result = mvc.perform(post("/api/accounts/{id}/signals", account).header("X-Actor", "journal-user")
                .header("X-Business-Transaction-Id", workflow).contentType(MediaType.APPLICATION_JSON).content(signalBody()))
            .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }
    private static String accountBody(String name) {
        return "{\"name\":\"%s\",\"industry\":\"industry-canary\",\"location\":\"location-canary\"}".formatted(name);
    }
    private static String signalBody() {
        return "{\"source\":\"source-canary\",\"observedOn\":\"2026-09-07\",\"observedFact\":\"evidence-canary\",\"assumption\":\"assumption-canary\"}";
    }
    private static String walking(String name, String capability, boolean complete) {
        return """
            {"accountName":"%s","industry":"industry-canary","location":"location-canary",
             "capabilityId":"%s","source":"source-canary","observedOn":"2026-09-07",
             "observedFact":"evidence-canary","assumption":"assumption-canary",
             "contacts":[{"name":"contact-canary","email":"contact-canary@example.test","notes":"notes-canary"}],
             "owner":%s,"hypothesis":"hypothesis-canary","nextAction":"action-canary","nextActionDate":"2026-09-10"}
            """.formatted(name, capability, complete ? "\"owner-canary\"" : "null");
    }
    private void reject(String type, String workflow) {
        tenant.execute("alter table tenant_event_journal add constraint journal_test_rejection check (business_transaction_id <> '"
            + workflow + "' or event_type <> '" + type + "')");
    }
    private void allowInserts() { tenant.execute("alter table tenant_event_journal drop constraint journal_test_rejection"); }
    private static void authenticate(String actor, String permission) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority(permission))));
    }
    private List<Event> events(String workflow) {
        return tenant.query("select * from tenant_event_journal where business_transaction_id=?", (row, index) -> {
            try {
                return new Event(row.getObject("event_id", UUID.class), row.getObject("tenant_id", UUID.class),
                    row.getString("aggregate_type"), row.getObject("aggregate_id", UUID.class), row.getString("event_type"),
                    row.getInt("schema_version"), row.getTimestamp("occurred_at").toInstant(), row.getString("actor_id"),
                    row.getString("request_id"), row.getString("business_transaction_id"), row.getString("trace_id"),
                    row.getString("traceparent"), json.readTree(row.getString("payload")));
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }, workflow);
    }
    private record Event(UUID id, UUID tenantId, String aggregateType, UUID aggregateId, String type, int version,
                         Instant occurredAt, String actor, String request, String workflow, String trace,
                         String traceparent, JsonNode payload) {}
}
