package com.brandempiricism.etocrm.identity;

import com.brandempiricism.etocrm.commons.TenantExecutionContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class VerifiedTenantExecutionContext implements TenantExecutionContext {
    @Override public Optional<UUID> currentTenantId() {
        return TenantContextHolder.current().map(IdentityApplicationApi.TenantContext::tenantId);
    }
    @Override public void onClear(Runnable cleanup) { TenantContextHolder.onClear(cleanup); }
}
