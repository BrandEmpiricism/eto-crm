package com.brandempiricism.etocrm.commons.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

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
        try (var connection = router.getConnection()) { connection.commit(); }
        verify(firstConnection).commit();
        TenantContextHolder.clear();
        bind(secondTenant);
        try (var connection = router.getConnection()) { connection.commit(); }
        verify(secondConnection).commit();
    }

    @Test void leastRecentlyUsedPoolIsClosedWhenBoundIsExceeded() throws Exception {
        var firstTenant = UUID.randomUUID();
        var secondTenant = UUID.randomUUID();
        var firstPool = mock(CloseableDataSource.class);
        var secondPool = mock(CloseableDataSource.class);
        when(firstPool.getConnection()).thenReturn(mock(Connection.class));
        when(secondPool.getConnection()).thenReturn(mock(Connection.class));
        var router = new TenantRoutingDataSource(id -> id.equals(firstTenant) ? firstPool : secondPool, 1);

        bind(firstTenant);
        router.getConnection().close();
        TenantContextHolder.clear();
        bind(secondTenant);
        router.getConnection().close();
        verify(firstPool).close();
    }

    @Test void callersCannotOverrideServerControlledCredentials() {
        var router = new TenantRoutingDataSource(tenant -> mock(DataSource.class), 1);
        assertThatThrownBy(() -> router.getConnection("caller", "secret"))
            .hasMessage("Caller-supplied tenant database credentials are not permitted.");
    }

    @Test void busyPoolCannotBeEvictedAndCapacityReturnsWhenTransactionFinishes() throws Exception {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var source = mock(CloseableDataSource.class);
        when(source.getConnection()).thenReturn(mock(Connection.class));
        var router = new TenantRoutingDataSource(id -> source, 1);
        bind(first);
        var lease = router.getConnection();
        TenantContextHolder.clear();
        bind(second);
        assertThatThrownBy(router::getConnection).hasMessage("Tenant pool capacity is occupied by active transactions.");
        verify(source, never()).close();
        lease.commit();
        lease.close();
        router.getConnection().close();
        verify(source).close();
    }

    @Test void expiredCredentialsRefreshOnlyAfterExistingTransactionFinishes() throws Exception {
        var clock = mock(java.time.Clock.class);
        var now = java.time.Instant.parse("2026-09-05T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        var first = mock(CloseableDataSource.class);
        var replacement = mock(CloseableDataSource.class);
        when(first.getConnection()).thenReturn(mock(Connection.class));
        when(replacement.getConnection()).thenReturn(mock(Connection.class));
        var creations = new java.util.concurrent.atomic.AtomicInteger();
        var router = new TenantRoutingDataSource(id -> creations.incrementAndGet() == 1 ? first : replacement,
            1, java.time.Duration.ofMinutes(5), clock);
        var tenant = UUID.randomUUID();
        bind(tenant);
        var lease = router.getConnection();
        when(clock.instant()).thenReturn(now.plusSeconds(301));
        TenantContextHolder.clear();
        bind(tenant);
        assertThatThrownBy(router::getConnection).hasMessage("Tenant credential refresh is awaiting active transactions.");
        verify(first, never()).close();
        lease.commit();
        lease.close();
        router.getConnection().close();
        assertThat(creations.get()).isEqualTo(2);
        verify(first).close();
    }

    @Test void shutdownDrainsLeasedConnectionsAndCannotReopenPools() throws Exception {
        var source = mock(CloseableDataSource.class);
        when(source.getConnection()).thenReturn(mock(Connection.class));
        var router = new TenantRoutingDataSource(id -> source, 1);
        bind(UUID.randomUUID());
        var lease = router.getConnection();
        router.close();
        verify(source, never()).close();
        assertThatThrownBy(router::getConnection).hasMessage("Tenant database routing is closed.");
        lease.close();
        lease.close();
        TenantContextHolder.clear();
        verify(source).close();
    }

    @Test void executionKeepsItsDataSourceAcrossTransactionsAndCredentialExpiry() throws Exception {
        var clock = mock(java.time.Clock.class);
        var now = java.time.Instant.parse("2026-09-06T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        var source = mock(CloseableDataSource.class);
        when(source.getConnection()).thenReturn(mock(Connection.class));
        var creations = new java.util.concurrent.atomic.AtomicInteger();
        var router = new TenantRoutingDataSource(id -> { creations.incrementAndGet(); return source; },
            1, java.time.Duration.ofMinutes(5), clock);
        var tenant = UUID.randomUUID();
        bind(tenant);
        router.getConnection().close();
        when(clock.instant()).thenReturn(now.plusSeconds(301));
        router.getConnection().close();
        assertThat(creations.get()).isEqualTo(1);
        verify(source, never()).close();
        TenantContextHolder.clear();
        bind(tenant);
        router.getConnection().close();
        assertThat(creations.get()).isEqualTo(2);
        verify(source).close();
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
