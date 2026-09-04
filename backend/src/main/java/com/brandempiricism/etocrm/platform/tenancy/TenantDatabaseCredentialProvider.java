package com.brandempiricism.etocrm.platform.tenancy;

/** Resolves an opaque registry reference through deployment-controlled secret infrastructure. */
public interface TenantDatabaseCredentialProvider {
    Credentials resolve(String credentialSecretRef);

    record Credentials(String username, String password) {
        public Credentials {
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                throw new IllegalArgumentException("Complete tenant database credentials are required.");
            }
        }
    }
}
