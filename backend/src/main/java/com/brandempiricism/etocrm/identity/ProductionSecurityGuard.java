package com.brandempiricism.etocrm.identity;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
class ProductionSecurityGuard {
    @Bean
    static BeanFactoryPostProcessor validateProductionSecurity(Environment environment) {
        return factory -> {
            if (!environment.acceptsProfiles(Profiles.of("prod"))) return;
            if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
                throw new IllegalStateException("Production cannot enable local or test profiles.");
            }
            if (!"oidc".equals(environment.getProperty("eto.security.mode"))) {
                throw new IllegalStateException("Production requires OIDC authentication.");
            }
            if (!environment.getProperty("eto.tenancy.routing.enabled", Boolean.class, false)) {
                throw new IllegalStateException("Production requires tenant database routing.");
            }
        };
    }
}
