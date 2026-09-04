package com.brandempiricism.etocrm.platform.tenancy;

/**
 * Infrastructure boundary for the privileged parts of tenant provisioning.
 * Implementations must be idempotent because a workflow can resume after any step.
 */
interface TenantProvisioningInfrastructure {

    Allocation allocateDatabase(TenantDatabaseSpec tenant);

    void migrate(TenantDatabaseSpec tenant, String credentialSecretRef);

    void seedDefaults(TenantDatabaseSpec tenant, String credentialSecretRef);

    void verify(TenantDatabaseSpec tenant, String credentialSecretRef);

    record TenantDatabaseSpec(java.util.UUID tenantId, String slug, String databaseName) {}

    record Allocation(String credentialSecretRef) {
        public Allocation {
            if (credentialSecretRef == null || credentialSecretRef.isBlank()) {
                throw new IllegalArgumentException("A credential secret reference is required.");
            }
        }
    }
}
