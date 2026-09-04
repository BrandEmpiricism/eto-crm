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
}
