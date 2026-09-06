package com.brandempiricism.etocrm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionSecurityGuardTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(ProductionSecurityGuard.class)
        .withPropertyValues("spring.profiles.active=prod", "eto.security.mode=oidc", "eto.tenancy.routing.enabled=true");

    @Test void productionCannotEnableDevelopmentIdentityOrDisableIsolation() {
        context.withPropertyValues("eto.security.mode=actor-header").run(result -> assertThat(result).hasFailed());
        context.withPropertyValues("eto.tenancy.routing.enabled=false").run(result -> assertThat(result).hasFailed());
        context.withPropertyValues("spring.profiles.active=prod,local").run(result -> assertThat(result).hasFailed());
        context.withPropertyValues("spring.profiles.active=prod,test").run(result -> assertThat(result).hasFailed());
        context.run(result -> assertThat(result).hasNotFailed());
    }
}
