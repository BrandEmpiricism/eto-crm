package com.brandempiricism.etocrm.platform.tenancy;

import com.brandempiricism.etocrm.commons.ServiceUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

/** Fail-closed default: a deployment must explicitly supply privileged infrastructure adapters. */
@Component
@Profile("!local")
class UnconfiguredTenantProvisioningInfrastructure implements TenantProvisioningInfrastructure {
    private static final String MESSAGE = "Tenant database provisioning infrastructure is not configured.";

    @Override
    public Allocation allocateDatabase(TenantDatabaseSpec tenant) {
        throw new ServiceUnavailableException(MESSAGE);
    }

    @Override public void migrate(TenantDatabaseSpec tenant, String credentialSecretRef) { throw new ServiceUnavailableException(MESSAGE); }
    @Override public void seedDefaults(TenantDatabaseSpec tenant, String credentialSecretRef) { throw new ServiceUnavailableException(MESSAGE); }
    @Override public void verify(TenantDatabaseSpec tenant, String credentialSecretRef) { throw new ServiceUnavailableException(MESSAGE); }
}
