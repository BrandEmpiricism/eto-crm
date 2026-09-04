package com.brandempiricism.etocrm.platform.tenancy;

import com.brandempiricism.etocrm.commons.ServiceUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(TenantDatabaseCredentialProvider.class)
final class UnconfiguredTenantDatabaseCredentialProvider implements TenantDatabaseCredentialProvider {
    @Override
    public Credentials resolve(String credentialSecretRef) {
        throw new ServiceUnavailableException("Tenant database credential resolution is not configured.");
    }
}
