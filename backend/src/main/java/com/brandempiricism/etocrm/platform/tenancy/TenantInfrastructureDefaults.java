package com.brandempiricism.etocrm.platform.tenancy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TenantInfrastructureDefaults {
    @Bean
    @ConditionalOnMissingBean(TenantClosureInfrastructure.class)
    TenantClosureInfrastructure tenantClosureInfrastructure() {
        return new UnconfiguredTenantClosureInfrastructure();
    }

    @Bean
    @ConditionalOnMissingBean(TenantDatabaseCredentialProvider.class)
    TenantDatabaseCredentialProvider tenantDatabaseCredentialProvider() {
        return new UnconfiguredTenantDatabaseCredentialProvider();
    }
}
