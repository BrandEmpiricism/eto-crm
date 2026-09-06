package com.brandempiricism.etocrm.platform.tenancy;

import com.brandempiricism.etocrm.commons.ServiceUnavailableException;

final class UnconfiguredTenantDatabaseCredentialProvider implements TenantDatabaseCredentialProvider {
    @Override
    public Credentials resolve(String credentialSecretRef) {
        throw new ServiceUnavailableException("Tenant database credential resolution is not configured.");
    }
}
