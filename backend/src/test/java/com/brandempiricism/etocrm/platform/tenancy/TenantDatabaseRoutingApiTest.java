package com.brandempiricism.etocrm.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brandempiricism.etocrm.identity.IdentityApplicationApi;
import com.brandempiricism.etocrm.identity.TenantAccessDeniedException;
import com.brandempiricism.etocrm.identity.TenantContextHolder;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TenantDatabaseRoutingApiTest {
    private final JdbcTemplate database = mock(JdbcTemplate.class);
    private final TenantDatabaseRoutingApi routing = new TenantDatabaseRoutingApi(database);

    @AfterEach void clearContext() { TenantContextHolder.clear(); }

    @Test void resolvesOnlyTheRouteMatchingTheVerifiedContext() throws Exception {
        var tenantId = UUID.randomUUID();
        bind(tenantId);
        var result = mock(ResultSet.class);
        when(result.getString("database_name")).thenReturn("eto_crm_acme");
        when(result.getString("credential_secret_ref")).thenReturn("vault://tenants/acme");
        when(database.query(anyString(), any(RowMapper.class), any(UUID.class)))
            .thenAnswer(call -> List.of(((RowMapper<?>) call.getArgument(1)).mapRow(result, 0)));

        var route = routing.resolve(tenantId);

        assertThat(route.databaseName()).isEqualTo("eto_crm_acme");
        assertThat(route.credentialSecretRef()).isEqualTo("vault://tenants/acme");
    }

    @Test void aDifferentTenantIdentifierFailsBeforeRegistryAccess() {
        bind(UUID.randomUUID());
        assertThatThrownBy(() -> routing.resolve(UUID.randomUUID()))
            .isInstanceOf(TenantAccessDeniedException.class)
            .hasMessage("Tenant database route is unavailable.");
        verify(database, never()).query(anyString(), any(RowMapper.class), any(UUID.class));
    }

    @Test void inactiveOrIncompleteRoutesAreNonDisclosing() {
        var tenantId = UUID.randomUUID();
        bind(tenantId);
        when(database.query(anyString(), any(RowMapper.class), any(UUID.class))).thenReturn(List.of());
        assertThatThrownBy(() -> routing.resolve(tenantId))
            .isInstanceOf(TenantAccessDeniedException.class)
            .hasMessage("Tenant database route is unavailable.");
    }

    private static void bind(UUID tenantId) {
        TenantContextHolder.bind(new IdentityApplicationApi.TenantContext(
            "actor", tenantId, IdentityApplicationApi.CompanyRole.BUSINESS_DEVELOPMENT));
    }
}
