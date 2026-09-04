package com.brandempiricism.etocrm.commons;

import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

/** Public module contract for creating a tenant pool from verified server-side routing state. */
@FunctionalInterface
public interface TenantDataSourceFactory {
    DataSource create(UUID tenantId) throws SQLException;
}
