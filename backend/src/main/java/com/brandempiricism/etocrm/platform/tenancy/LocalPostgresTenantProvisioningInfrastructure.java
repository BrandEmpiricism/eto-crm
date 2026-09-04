package com.brandempiricism.etocrm.platform.tenancy;

import com.brandempiricism.etocrm.commons.ServiceUnavailableException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

/** Local-development PostgreSQL adapter for the initial client. Production uses a managed secret adapter. */
@Component
@Profile("local")
class LocalPostgresTenantProvisioningInfrastructure implements TenantProvisioningInfrastructure {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,62}");
    private static final String SECRET_REF = "local-env://R_HYPER_TOOLING_DATABASE_CREDENTIALS";

    private final String adminUrl;
    private final String adminUsername;
    private final String adminPassword;
    private final String tenantPassword;

    LocalPostgresTenantProvisioningInfrastructure(
            @Value("${eto.tenancy.provisioning.admin-url}") String adminUrl,
            @Value("${eto.tenancy.provisioning.admin-username}") String adminUsername,
            @Value("${eto.tenancy.provisioning.admin-password}") String adminPassword,
            @Value("${eto.tenancy.initial-client.database-password}") String tenantPassword) {
        this.adminUrl = adminUrl;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.tenantPassword = tenantPassword;
    }

    @Override
    public Allocation allocateDatabase(TenantDatabaseSpec tenant) {
        requireInitialClient(tenant);
        var role = identifier(tenant.databaseName());
        try (Connection connection = DriverManager.getConnection(adminUrl, adminUsername, adminPassword);
             Statement statement = connection.createStatement()) {
            if (!exists(statement, "select 1 from pg_roles where rolname='" + role + "'")) {
                statement.execute("create role " + role + " login password '" + literal(tenantPassword) + "'");
            } else {
                statement.execute("alter role " + role + " login password '" + literal(tenantPassword) + "'");
            }
            if (!exists(statement, "select 1 from pg_database where datname='" + role + "'")) {
                statement.execute("create database " + role + " owner " + role);
            }
            statement.execute("revoke connect on database " + role + " from public");
            statement.execute("grant connect on database " + role + " to " + role);
            return new Allocation(SECRET_REF);
        } catch (SQLException failure) {
            throw unavailable("Unable to allocate the tenant database.", failure);
        }
    }

    @Override
    public void migrate(TenantDatabaseSpec tenant, String credentialSecretRef) {
        requireSecret(tenant, credentialSecretRef);
        Flyway.configure().dataSource(dataSource(tenant)).locations("classpath:db/tenant").load().migrate();
    }

    @Override
    public void seedDefaults(TenantDatabaseSpec tenant, String credentialSecretRef) {
        requireSecret(tenant, credentialSecretRef);
        // Required defaults are versioned and idempotently owned by the tenant Flyway sequence.
    }

    @Override
    public void verify(TenantDatabaseSpec tenant, String credentialSecretRef) {
        requireSecret(tenant, credentialSecretRef);
        try (Connection connection = dataSource(tenant).getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "select count(*) from information_schema.tables where table_schema='public' and table_name='account'")) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new ServiceUnavailableException("Tenant schema readiness verification failed.");
            }
        } catch (SQLException failure) {
            throw unavailable("Unable to verify tenant database readiness.", failure);
        }
    }

    private DriverManagerDataSource dataSource(TenantDatabaseSpec tenant) {
        return new DriverManagerDataSource(databaseUrl(adminUrl, tenant.databaseName()), tenant.databaseName(), tenantPassword);
    }

    private static boolean exists(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) { return result.next(); }
    }

    private static String databaseUrl(String url, String database) {
        int query = url.indexOf('?');
        String suffix = query < 0 ? "" : url.substring(query);
        String base = query < 0 ? url : url.substring(0, query);
        int slash = base.lastIndexOf('/');
        if (!base.startsWith("jdbc:postgresql://") || slash < "jdbc:postgresql://".length()) {
            throw new IllegalArgumentException("The local provisioning administrator URL must be a PostgreSQL JDBC URL.");
        }
        return base.substring(0, slash + 1) + identifier(database) + suffix;
    }

    private static String identifier(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) throw new IllegalArgumentException("Unsafe database identifier.");
        return value;
    }

    private static String literal(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("The local tenant database password is required.");
        return value.replace("'", "''");
    }

    private static void requireInitialClient(TenantDatabaseSpec tenant) {
        if (!"r-hyper-tooling".equals(tenant.slug())) {
            throw new ServiceUnavailableException("Local provisioning is configured only for the initial client.");
        }
    }

    private static void requireSecret(TenantDatabaseSpec tenant, String secretRef) {
        requireInitialClient(tenant);
        if (!SECRET_REF.equals(secretRef)) throw new ServiceUnavailableException("The tenant credential reference is not available locally.");
    }

    private static ServiceUnavailableException unavailable(String message, Exception failure) {
        return new ServiceUnavailableException(message, failure);
    }
}
