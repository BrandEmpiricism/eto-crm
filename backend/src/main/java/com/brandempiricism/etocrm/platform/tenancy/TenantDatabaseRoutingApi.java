package com.brandempiricism.etocrm.platform.tenancy;

import com.brandempiricism.etocrm.identity.TenantAccessDeniedException;
import com.brandempiricism.etocrm.identity.TenantContextHolder;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Control-plane API for resolving an authorized tenant to opaque, server-owned database references. */
@Service
public class TenantDatabaseRoutingApi {
    private static final String DENIED = "Tenant database route is unavailable.";
    private final JdbcTemplate database;

    public TenantDatabaseRoutingApi(@Qualifier("platformJdbcTemplate") JdbcTemplate database) {
        this.database = database;
    }

    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public TenantDatabaseRoute resolve(UUID tenantId) {
        var context = TenantContextHolder.current()
            .filter(value -> value.tenantId().equals(tenantId))
            .orElseThrow(() -> new TenantAccessDeniedException(DENIED));
        var routes = database.query(
            "select database_name, credential_secret_ref from tenant_registry "
                + "where id = ? and status = 'ACTIVE' and credential_secret_ref is not null",
            (result, row) -> new TenantDatabaseRoute(
                context.tenantId(), result.getString("database_name"), result.getString("credential_secret_ref")),
            tenantId);
        if (routes.size() != 1) throw new TenantAccessDeniedException(DENIED);
        return routes.getFirst();
    }

    public record TenantDatabaseRoute(UUID tenantId, String databaseName, String credentialSecretRef) {
        public TenantDatabaseRoute {
            if (tenantId == null || databaseName == null || databaseName.isBlank()
                    || credentialSecretRef == null || credentialSecretRef.isBlank()) {
                throw new IllegalArgumentException("A complete tenant database route is required.");
            }
        }
    }
}
