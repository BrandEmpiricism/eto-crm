package com.brandempiricism.etocrm.platform.tenancy;

import java.util.UUID;

/** Deployment adapter must idempotently revoke direct database credentials before closure completes. */
public interface TenantClosureInfrastructure {
    void revokeCredentials(UUID tenantId);
}
