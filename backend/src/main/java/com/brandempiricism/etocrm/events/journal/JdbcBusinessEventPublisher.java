package com.brandempiricism.etocrm.events.journal;

import com.brandempiricism.etocrm.commons.DiagnosticContext;
import com.brandempiricism.etocrm.events.BusinessEvent;
import com.brandempiricism.etocrm.events.BusinessEventPublisher;
import com.brandempiricism.etocrm.identity.TenantContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class JdbcBusinessEventPublisher implements BusinessEventPublisher {
    private final JdbcTemplate database;
    private final ObjectMapper json;
    private final Environment environment;

    JdbcBusinessEventPublisher(@Qualifier("tenantJdbcTemplate") JdbcTemplate database,
                               ObjectMapper json, Environment environment) {
        this.database = database;
        this.json = json;
        this.environment = environment;
    }

    @Override
    @PreAuthorize("@tenantAuthorization.canWriteAs(#actor)")
    @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.MANDATORY)
    public void record(BusinessEvent event, String actor) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Business events require a writable tenant transaction.");
        }
        UUID tenant = tenantId();
        var fact = fact(event);
        var correlation = DiagnosticContext.capture();
        String traceparent = correlation.traceparent();
        if (traceparent == null || !traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")) traceparent = null;
        String traceId = traceparent == null ? null : traceparent.substring(3, 35);
        database.update("""
            insert into tenant_event_journal
                (event_id, tenant_id, aggregate_type, aggregate_id, event_type, schema_version,
                 occurred_at, actor_id, request_id, business_transaction_id, trace_id, traceparent, payload)
            values (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), tenant, fact.aggregateType(), fact.aggregateId(), fact.type(),
            Timestamp.from(Instant.now()), actor, correlationId(correlation.requestId()),
            correlationId(correlation.businessTransactionId()), traceId, traceparent, payload(fact.payload()));
    }

    private UUID tenantId() {
        var context = TenantContextHolder.current();
        if (context.isPresent()) return context.get().tenantId();
        // The existing actor-header demo uses one explicitly configured database, never a caller's tenant header.
        boolean development = environment.acceptsProfiles(Profiles.of("local", "test"))
            && !environment.acceptsProfiles(Profiles.of("prod"))
            && "actor-header".equals(environment.getProperty("eto.security.mode"))
            && !environment.getProperty("eto.tenancy.routing.enabled", Boolean.class, false);
        UUID configured = development ? environment.getProperty("eto.tenancy.development-tenant-id", UUID.class) : null;
        if (configured == null) throw new AccessDeniedException("A verified tenant is required to record business events.");
        return configured;
    }

    private static Fact fact(BusinessEvent event) {
        return switch (event) {
            case BusinessEvent.AccountCreated account ->
                new Fact("account.created", "ACCOUNT", account.accountId(), Map.of());
            case BusinessEvent.SignalRecorded signal ->
                new Fact("signal.recorded", "SIGNAL", signal.signalId(), Map.of("accountId", signal.accountId()));
            case BusinessEvent.CapabilityMatchSaved match ->
                new Fact("capability_match.saved", "CAPABILITY_MATCH", match.matchId(),
                    Map.of("accountId", match.accountId(), "signalId", match.signalId(),
                        "capabilityId", match.capabilityId(), "status", match.status().name()));
        };
    }

    private String payload(Map<String, ?> value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Business event encoding failed.", failure); }
    }

    private static String correlationId(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,100}") ? value : null;
    }

    private record Fact(String type, String aggregateType, UUID aggregateId, Map<String, ?> payload) {}
}
