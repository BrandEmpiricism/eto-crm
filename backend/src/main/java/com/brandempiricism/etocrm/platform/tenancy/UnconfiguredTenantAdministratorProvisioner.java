package com.brandempiricism.etocrm.platform.tenancy;

import java.util.UUID;
import com.brandempiricism.etocrm.commons.ServiceUnavailableException;
import org.springframework.stereotype.Component;

@Component
class UnconfiguredTenantAdministratorProvisioner implements TenantAdministratorProvisioner {
    @Override
    public void assignInitialAdministrator(UUID tenantId, String actorId) {
        throw new ServiceUnavailableException("Tenant administrator provisioning is not configured.");
    }
}
