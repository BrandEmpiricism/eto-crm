package com.brandempiricism.etocrm.commons.persistence;

import com.brandempiricism.etocrm.commons.TenantDataSourceFactory;
import com.brandempiricism.etocrm.identity.TenantContextHolder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Bounded tenant pools that preserve leased connections until their transactions finish. */
public final class TenantRoutingDataSource extends AbstractDataSource implements AutoCloseable {
    private final TenantDataSourceFactory factory;
    private final int maximumPools;
    private final Duration lifetime;
    private final Clock clock;
    private final Map<UUID, Pool> pools = new LinkedHashMap<>(16, .75f, true);
    private boolean closed;

    public TenantRoutingDataSource(TenantDataSourceFactory factory, int maximumPools) {
        this(factory, maximumPools, Duration.ofMinutes(5), Clock.systemUTC());
    }

    public TenantRoutingDataSource(TenantDataSourceFactory factory, int maximumPools, Duration lifetime, Clock clock) {
        if (maximumPools < 1 || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("Tenant pool limits must be positive.");
        }
        this.factory = factory;
        this.maximumPools = maximumPools;
        this.lifetime = lifetime;
        this.clock = clock;
    }

    @Override
    public Connection getConnection() throws SQLException {
        var context = TenantContextHolder.current()
            .orElseThrow(() -> new SQLException("Verified tenant context is required."));
        Pool pool;
        synchronized (pools) {
            if (closed) throw new SQLException("Tenant database routing is closed.");
            pool = pools.get(context.tenantId());
            if (pool != null && !clock.instant().isBefore(pool.expiresAt)) {
                if (pool.leases != 0) throw new SQLException("Tenant credential refresh is awaiting active transactions.");
                pools.remove(context.tenantId());
                closePool(pool);
                pool = null;
            }
            if (pool == null) {
                makeRoom();
                var source = factory.create(context.tenantId());
                if (source == null) throw new SQLException("Tenant database route is unavailable.");
                pool = new Pool(source, clock.instant().plus(lifetime));
                pools.put(context.tenantId(), pool);
            }
            pool.leases++;
        }
        try {
            var connection = pool.source.getConnection();
            if (connection == null) throw new SQLException("Tenant connection is unavailable.");
            return lease(pool, connection);
        } catch (SQLException | RuntimeException failure) {
            release(pool);
            throw failure;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("Caller-supplied tenant database credentials are not permitted.");
    }

    private void makeRoom() throws SQLException {
        if (pools.size() < maximumPools) return;
        var iterator = pools.entrySet().iterator();
        while (iterator.hasNext()) {
            var candidate = iterator.next().getValue();
            if (candidate.leases == 0) {
                iterator.remove();
                closePool(candidate);
                return;
            }
        }
        throw new SQLException("Tenant pool capacity is occupied by active transactions.");
    }

    private Connection lease(Pool pool, Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
            new java.lang.reflect.InvocationHandler() {
                private boolean returned;
                @Override public synchronized Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("close")) {
                        if (!returned) {
                            returned = true;
                            try { connection.close(); } finally { release(pool); }
                        }
                        return null;
                    }
                    if (method.getName().equals("isClosed") && returned) return true;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("toString")) return "Tenant connection lease";
                    if (returned) throw new SQLException("Tenant connection is closed.");
                    try { return method.invoke(connection, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                }
            });
    }

    private void release(Pool pool) throws SQLException {
        synchronized (pools) {
            pool.leases--;
            if (closed && pool.leases == 0) closePool(pool);
        }
    }

    @Override
    public void close() throws SQLException {
        synchronized (pools) {
            if (closed) return;
            closed = true;
            SQLException failure = null;
            for (var pool : pools.values()) {
                if (pool.leases == 0) {
                    try { closePool(pool); } catch (SQLException exception) { failure = exception; }
                }
            }
            pools.clear();
            if (failure != null) throw failure;
        }
    }

    private static void closePool(Pool pool) throws SQLException {
        if (pool.source instanceof AutoCloseable closeable) {
            try { closeable.close(); }
            catch (Exception failure) { throw new SQLException("Could not close tenant database pool."); }
        }
    }

    private static final class Pool {
        final DataSource source;
        final Instant expiresAt;
        int leases;
        Pool(DataSource source, Instant expiresAt) { this.source = source; this.expiresAt = expiresAt; }
    }
}
