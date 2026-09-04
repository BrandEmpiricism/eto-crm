package com.brandempiricism.etocrm.platform.tenancy;

import com.brandempiricism.etocrm.commons.TenantDataSourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "eto.tenancy.routing.enabled", havingValue = "true")
class TenantRoutingConfiguration {
    @Bean
    TenantDataSourceFactory tenantDataSourceFactory(TenantDatabaseRoutingApi routing,
            TenantDatabaseCredentialProvider credentials,
            @Value("${eto.tenancy.routing.database-server-url}") String serverUrl,
            @Value("${eto.tenancy.routing.maximum-connections-per-tenant:5}") int maximumConnections) {
        return new TenantPoolFactory(routing, credentials, serverUrl, maximumConnections);
    }
}
