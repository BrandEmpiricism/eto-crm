package com.brandempiricism.etocrm.commons.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brandempiricism.etocrm.identity.IdentityApplicationApi;
import com.brandempiricism.etocrm.identity.TenantContextHolder;
import java.sql.Connection;
import java.util.HashMap;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantRoutingDataSourceTest {
    @AfterEach void clearContext() { TenantContextHolder.clear(); }

    @Test void tenantDataAccessFailsClosedWithoutVerifiedContext() {
        var router = new TenantRoutingDataSource(tenant -> mock(DataSource.class), 2);
        assertThatThrownBy(router::getConnection)
            .isInstanceOf(java.sql.SQLException.class)
            .hasMessage("Verified tenant context is required.");
    }

    @Test void verifiedTenantContextsUseDifferentLazyPools() throws Exception {
        var firstTenant = UUID.randomUUID();
        var secondTenant = UUID.randomUUID();
        var pools = new HashMap<UUID, DataSource>();
        var firstConnection = mock(Connection.class);
        var secondConnection = mock(Connection.class);
        pools.put(firstTenant, pool(firstConnection));
        pools.put(secondTenant, pool(secondConnection));
        var router = new TenantRoutingDataSource(pools::get, 2);

        bind(firstTenant);
        assertThat(router.getConnection()).isSameAs(firstConnection);
        TenantContextHolder.clear();
        bind(secondTenant);
        assertThat(router.getConnection()).isSameAs(secondConnection);
    }

    @Test void leastRecentlyUsedPoolIsClosedWhenBoundIsExceeded() throws Exception {
        var firstTenant = UUID.randomUUID();
        var secondTenant = UUID.randomUUID();
        var firstPool = mock(CloseableDataSource.class);
        var secondPool = mock(CloseableDataSource.class);
        var router = new TenantRoutingDataSource(id -> id.equals(firstTenant) ? firstPool : secondPool, 1);

        bind(firstTenant);
        router.getConnection();
        TenantContextHolder.clear();
        bind(secondTenant);
        router.getConnection();
        verify(firstPool).close();
    }

    @Test void callersCannotOverrideServerControlledCredentials() {
        var router = new TenantRoutingDataSource(tenant -> mock(DataSource.class), 1);
        assertThatThrownBy(() -> router.getConnection("caller", "secret"))
            .hasMessage("Caller-supplied tenant database credentials are not permitted.");
    }

    private static DataSource pool(Connection connection) throws Exception {
        var pool = mock(DataSource.class);
        when(pool.getConnection()).thenReturn(connection);
        return pool;
    }

    private static void bind(UUID tenantId) {
        TenantContextHolder.bind(new IdentityApplicationApi.TenantContext(
            "actor", tenantId, IdentityApplicationApi.CompanyRole.BUSINESS_DEVELOPMENT));
    }

    private interface CloseableDataSource extends DataSource, AutoCloseable {
        @Override void close();
    }
}
