package com.brandempiricism.etocrm.platform.tenancy;

import com.brandempiricism.etocrm.commons.ServiceUnavailableException;
import java.util.UUID;

class UnconfiguredTenantClosureInfrastructure implements TenantClosureInfrastructure {
    @Override public void revokeCredentials(UUID tenantId) {
        throw new ServiceUnavailableException("Tenant credential revocation is not configured; tenant remains suspended.");
    }
}
