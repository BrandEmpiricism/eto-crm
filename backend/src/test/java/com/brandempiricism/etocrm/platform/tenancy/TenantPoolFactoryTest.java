package com.brandempiricism.etocrm.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantPoolFactoryTest {
    private final TenantDatabaseRoutingApi routing = mock(TenantDatabaseRoutingApi.class);
    private final TenantDatabaseCredentialProvider secrets = mock(TenantDatabaseCredentialProvider.class);

    @Test void buildsALazyBoundedPoolFromServerControlledRouteAndSecret() {
        var tenantId = UUID.randomUUID();
        when(routing.resolve(tenantId)).thenReturn(new TenantDatabaseRoutingApi.TenantDatabaseRoute(
            tenantId, "eto_crm_acme", "vault://tenants/acme"));
        when(secrets.resolve("vault://tenants/acme"))
            .thenReturn(new TenantDatabaseCredentialProvider.Credentials("acme_role", "not-logged"));
        var factory = new TenantPoolFactory(routing, secrets, "jdbc:postgresql://db.internal:5432/postgres?sslmode=require", 4,
            source -> {}); // Schema validation is exercised against PostgreSQL by PostgresTenantIsolationIT.

        try (var pool = (HikariDataSource) factory.create(tenantId)) {
            assertThat(pool.getJdbcUrl()).isEqualTo("jdbc:postgresql://db.internal:5432/eto_crm_acme?sslmode=require");
            assertThat(pool.getUsername()).isEqualTo("acme_role");
            assertThat(pool.getMaximumPoolSize()).isEqualTo(4);
            assertThat(pool.getMinimumIdle()).isZero();
        }
        verify(routing).resolve(tenantId);
        verify(secrets).resolve("vault://tenants/acme");
    }

    @Test void rejectsUnsafeRegistryDatabaseNames() {
        assertThatThrownBy(() -> TenantPoolFactory.databaseUrl(
            "jdbc:postgresql://db.internal:5432/postgres", "other?password=caller"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsafe tenant database identifier.");
    }

    @Test void rejectsNonPostgresqlServerConfiguration() {
        assertThatThrownBy(() -> TenantPoolFactory.databaseUrl("jdbc:h2:mem:test", "eto_crm_acme"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Tenant database server must be a PostgreSQL JDBC URL.");
    }

    @Test void secretProviderFailureIsCorrelatedWithoutDisclosingHostOrCredentials() {
        var tenant = UUID.randomUUID();
        when(routing.resolve(tenant)).thenReturn(new TenantDatabaseRoutingApi.TenantDatabaseRoute(tenant, "eto_crm_test", "secret-canary"));
        when(secrets.resolve("secret-canary")).thenThrow(new IllegalStateException("jdbc:postgresql://host-canary/db password-canary"));
        var factory = new TenantPoolFactory(routing, secrets, "jdbc:postgresql://host-canary/db", 2, source -> {});
        try (var logs = new com.brandempiricism.etocrm.commons.observability.LogCapture();
             var context = com.brandempiricism.etocrm.commons.DiagnosticContext.start(
                 new com.brandempiricism.etocrm.commons.DiagnosticContext.Correlation("route-request", null, "route-workflow"))) {
            assertThatThrownBy(() -> factory.create(tenant))
                .isInstanceOf(com.brandempiricism.etocrm.commons.ServiceUnavailableException.class)
                .hasMessage("Tenant database readiness or schema compatibility verification failed.");
            var event = logs.events("tenant.database.failed").getFirst();
            assertThat(event.path("tenantId").asText()).isEqualTo(tenant.toString());
            assertThat(event.path("databaseRouteId").asText()).isEqualTo("tenant:" + tenant);
            assertThat(event.path("requestId").asText()).isEqualTo("route-request");
            assertThat(logs.output()).doesNotContain("canary", "jdbc:");
            assertThat(org.slf4j.MDC.get("tenantId")).isNull();
        }
    }
}
