package com.brandempiricism.etocrm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantJobExecutorTest {
    private final IdentityApplicationApi identities = mock(IdentityApplicationApi.class);
    private final TenantJobExecutor jobs = new TenantJobExecutor(identities);

    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void backgroundWorkReceivesImmutableAuthorizedServiceAndTenantContext() {
        var tenantId = UUID.randomUUID();
        var context = new IdentityApplicationApi.TenantContext(
            "service:outbox", tenantId, IdentityApplicationApi.CompanyRole.TENANT_ADMIN);
        when(identities.selectTenantForService("service:outbox", tenantId)).thenReturn(context);
        var observed = new AtomicReference<IdentityApplicationApi.TenantContext>();

        jobs.run("service:outbox", tenantId, () -> observed.set(TenantContextHolder.current().orElseThrow()));

        assertThat(observed.get()).isEqualTo(context);
        assertThat(TenantContextHolder.current()).isEmpty();
        verify(identities).selectTenantForService("service:outbox", tenantId);
    }

    @Test void existingContextCannotBeReplacedByAConfusedDeputy() {
        var existing = new IdentityApplicationApi.TenantContext(
            "actor", UUID.randomUUID(), IdentityApplicationApi.CompanyRole.BUSINESS_DEVELOPMENT);
        TenantContextHolder.bind(existing);
        assertThatThrownBy(() -> jobs.run("service:outbox", UUID.randomUUID(), () -> {}))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Tenant context is already bound.");
    }

    @Test void scheduledWorkRevalidatesIdentityPropagatesCorrelationAndRestoresWorkerOnFailure() throws Exception {
        var tenant = UUID.randomUUID();
        var context = new IdentityApplicationApi.TenantContext("service:scheduled-test", tenant,
            IdentityApplicationApi.CompanyRole.TENANT_ADMIN);
        when(identities.selectTenantForService(context.actorId(), tenant)).thenReturn(context);
        var parent = new com.brandempiricism.etocrm.commons.DiagnosticContext.Correlation("scheduled-request",
            "00-1234567890abcdef1234567890abcdef-1234567890abcdef-01", "scheduled-workflow");
        try (var worker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
             var logs = new com.brandempiricism.etocrm.commons.observability.LogCapture()) {
            worker.submit(() -> org.slf4j.MDC.put("worker", "original")).get(10, java.util.concurrent.TimeUnit.SECONDS);
            for (boolean fail : new boolean[] {false, true}) {
                worker.schedule(() -> {
                    Runnable execution = () -> jobs.run(context.actorId(), tenant, parent, () -> {
                        assertThat(TenantContextHolder.current()).contains(context);
                        assertThat(org.slf4j.MDC.get("actorId")).isEqualTo(context.actorId());
                        assertThat(org.slf4j.MDC.get("traceId")).isEqualTo("1234567890abcdef1234567890abcdef");
                        assertThat(org.slf4j.MDC.get("parentSpanId")).isEqualTo("1234567890abcdef");
                        if (fail) throw new IllegalStateException("customer-canary");
                    });
                    if (fail) assertThatThrownBy(execution::run).isInstanceOf(IllegalStateException.class);
                    else execution.run();
                    assertThat(TenantContextHolder.current()).isEmpty();
                    assertThat(org.slf4j.MDC.getCopyOfContextMap()).containsExactlyEntriesOf(java.util.Map.of("worker", "original"));
                }, 1, java.util.concurrent.TimeUnit.MILLISECONDS).get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
            var events = logs.events("tenant.job.completed");
            assertThat(events).hasSize(2).allSatisfy(event -> {
                assertThat(event.path("tenantId").asText()).isEqualTo(tenant.toString());
                assertThat(event.path("requestId").asText()).isEqualTo("scheduled-request");
                assertThat(event.path("businessTransactionId").asText()).isEqualTo("scheduled-workflow");
            });
            assertThat(events.get(0).path("event.outcome").asText()).isEqualTo("success");
            assertThat(events.get(1).path("event.outcome").asText()).isEqualTo("failure");
            assertThat(events.get(0).path("spanId")).isNotEqualTo(events.get(1).path("spanId"));
            assertThat(logs.output()).doesNotContain("customer-canary");
        }
        verify(identities, org.mockito.Mockito.times(2)).selectTenantForService(context.actorId(), tenant);
    }

    @Test void simultaneousTenantsKeepIndependentDiagnosticContext() throws Exception {
        var tenants = java.util.List.of(UUID.randomUUID(), UUID.randomUUID());
        for (var tenant : tenants) when(identities.selectTenantForService("service:scheduled-test", tenant))
            .thenReturn(new IdentityApplicationApi.TenantContext("service:scheduled-test", tenant, IdentityApplicationApi.CompanyRole.TENANT_ADMIN));
        var ready = new java.util.concurrent.CyclicBarrier(2);
        try (var worker = java.util.concurrent.Executors.newFixedThreadPool(2);
             var logs = new com.brandempiricism.etocrm.commons.observability.LogCapture()) {
            var futures = tenants.stream().map(tenant -> worker.submit(() -> jobs.run("service:scheduled-test", tenant,
                new com.brandempiricism.etocrm.commons.DiagnosticContext.Correlation("job-" + tenant, null, "workflow-" + tenant), () -> {
                    try { ready.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                    assertThat(org.slf4j.MDC.get("tenantId")).isEqualTo(tenant.toString());
                }))).toList();
            for (var future : futures) future.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(logs.events("tenant.job.completed")).hasSize(2).allSatisfy(event -> {
                var tenant = event.path("tenantId").asText();
                assertThat(event.path("requestId").asText()).isEqualTo("job-" + tenant);
                assertThat(event.path("businessTransactionId").asText()).isEqualTo("workflow-" + tenant);
            });
        }
    }
}
