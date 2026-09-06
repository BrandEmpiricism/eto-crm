package com.brandempiricism.etocrm.platform.tenancy;

import com.brandempiricism.etocrm.commons.TenantDataSourceFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import java.util.function.Consumer;
import org.flywaydb.core.Flyway;
import com.brandempiricism.etocrm.commons.ServiceUnavailableException;

/** Builds tenant pools exclusively from an authorized registry route and server-side secret provider. */
public final class TenantPoolFactory implements TenantDataSourceFactory {
    private static final Pattern DATABASE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,62}");
    private final TenantDatabaseRoutingApi routing;
    private final TenantDatabaseCredentialProvider credentials;
    private final String databaseServerUrl;
    private final int maximumPoolSize;
    private final Consumer<DataSource> schemaVerifier;

    public TenantPoolFactory(TenantDatabaseRoutingApi routing, TenantDatabaseCredentialProvider credentials,
            String databaseServerUrl, int maximumPoolSize) {
        this(routing, credentials, databaseServerUrl, maximumPoolSize, TenantPoolFactory::verifySchema);
    }

    TenantPoolFactory(TenantDatabaseRoutingApi routing, TenantDatabaseCredentialProvider credentials,
            String databaseServerUrl, int maximumPoolSize, Consumer<DataSource> schemaVerifier) {
        if (maximumPoolSize < 1) throw new IllegalArgumentException("Tenant pool size must be positive.");
        this.routing = routing;
        this.credentials = credentials;
        this.databaseServerUrl = databaseServerUrl;
        this.maximumPoolSize = maximumPoolSize;
        this.schemaVerifier = schemaVerifier;
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
        var pool = new HikariDataSource(configuration);
        try {
            schemaVerifier.accept(pool);
            return pool;
        } catch (RuntimeException failure) {
            pool.close();
            throw new ServiceUnavailableException("Tenant database readiness or schema compatibility verification failed.");
        }
    }

    private static void verifySchema(DataSource source) {
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/tenant")
            .ignoreMigrationPatterns(new String[0]).load();
        if (!flyway.validateWithResult().validationSuccessful || flyway.info().pending().length != 0) {
            throw new ServiceUnavailableException("Tenant schema is incompatible.");
        }
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
