package com.brandempiricism.etocrm.platform.tenancy;

import com.brandempiricism.etocrm.commons.TenantDataSourceFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/** Builds tenant pools exclusively from an authorized registry route and server-side secret provider. */
public final class TenantPoolFactory implements TenantDataSourceFactory {
    private static final Pattern DATABASE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,62}");
    private final TenantDatabaseRoutingApi routing;
    private final TenantDatabaseCredentialProvider credentials;
    private final String databaseServerUrl;
    private final int maximumPoolSize;

    public TenantPoolFactory(TenantDatabaseRoutingApi routing, TenantDatabaseCredentialProvider credentials,
            String databaseServerUrl, int maximumPoolSize) {
        if (maximumPoolSize < 1) throw new IllegalArgumentException("Tenant pool size must be positive.");
        this.routing = routing;
        this.credentials = credentials;
        this.databaseServerUrl = databaseServerUrl;
        this.maximumPoolSize = maximumPoolSize;
    }

    @Override
    public DataSource create(UUID tenantId) {
        var route = routing.resolve(tenantId);
        var secret = credentials.resolve(route.credentialSecretRef());
        var configuration = new HikariConfig();
        configuration.setJdbcUrl(databaseUrl(databaseServerUrl, route.databaseName()));
        configuration.setUsername(secret.username());
        configuration.setPassword(secret.password());
        configuration.setMaximumPoolSize(maximumPoolSize);
        configuration.setMinimumIdle(0);
        configuration.setInitializationFailTimeout(-1);
        configuration.setPoolName("tenant-" + tenantId);
        return new HikariDataSource(configuration);
    }

    static String databaseUrl(String serverUrl, String databaseName) {
        if (databaseName == null || !DATABASE_NAME.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Unsafe tenant database identifier.");
        }
        if (serverUrl == null || !serverUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Tenant database server must be a PostgreSQL JDBC URL.");
        }
        int query = serverUrl.indexOf('?');
        String suffix = query < 0 ? "" : serverUrl.substring(query);
        String base = query < 0 ? serverUrl : serverUrl.substring(0, query);
        int slash = base.lastIndexOf('/');
        if (slash < "jdbc:postgresql://".length()) {
            throw new IllegalArgumentException("Tenant database server must include a database path.");
        }
        return base.substring(0, slash + 1) + databaseName + suffix;
    }
}
