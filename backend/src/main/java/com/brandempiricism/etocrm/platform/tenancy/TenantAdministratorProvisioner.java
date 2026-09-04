package com.brandempiricism.etocrm.platform.tenancy;

import java.util.UUID;

/** Creates the initial, verified tenant membership in the platform identity store. */
interface TenantAdministratorProvisioner {
    void assignInitialAdministrator(UUID tenantId, String actorId);
}
