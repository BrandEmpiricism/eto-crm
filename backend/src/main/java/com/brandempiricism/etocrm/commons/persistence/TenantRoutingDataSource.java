package com.brandempiricism.etocrm.commons.persistence;

import com.brandempiricism.etocrm.commons.TenantDataSourceFactory;
import com.brandempiricism.etocrm.identity.TenantContextHolder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Selects a bounded, lazily-created pool using only the verified immutable tenant context. */
public final class TenantRoutingDataSource extends AbstractDataSource implements AutoCloseable {
    private final TenantDataSourceFactory factory;
    private final int maximumPools;
    private final Map<UUID, DataSource> pools = new LinkedHashMap<>(16, .75f, true);

    public TenantRoutingDataSource(TenantDataSourceFactory factory, int maximumPools) {
        if (maximumPools < 1) throw new IllegalArgumentException("Maximum tenant pools must be positive.");
        this.factory = factory;
        this.maximumPools = maximumPools;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return currentPool().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("Caller-supplied tenant database credentials are not permitted.");
    }

    private DataSource currentPool() throws SQLException {
        var context = TenantContextHolder.current()
            .orElseThrow(() -> new SQLException("Verified tenant context is required."));
        synchronized (pools) {
            var existing = pools.get(context.tenantId());
            if (existing != null) return existing;
            var created = factory.create(context.tenantId());
            if (created == null) throw new SQLException("Tenant database route is unavailable.");
            pools.put(context.tenantId(), created);
            evictIfNecessary();
            return created;
        }
    }

    private void evictIfNecessary() throws SQLException {
        if (pools.size() <= maximumPools) return;
        var eldest = pools.entrySet().iterator().next();
        pools.remove(eldest.getKey());
        close(eldest.getValue());
    }

    @Override
    public void close() throws SQLException {
        synchronized (pools) {
            SQLException failure = null;
            for (var pool : pools.values()) {
                try { close(pool); } catch (SQLException exception) { failure = exception; }
            }
            pools.clear();
            if (failure != null) throw failure;
        }
    }

    private static void close(DataSource dataSource) throws SQLException {
        if (dataSource instanceof AutoCloseable closeable) {
            try { closeable.close(); }
            catch (SQLException exception) { throw exception; }
            catch (Exception exception) { throw new SQLException("Could not close tenant database pool.", exception); }
        }
    }
}
