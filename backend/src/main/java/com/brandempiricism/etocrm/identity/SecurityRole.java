package com.brandempiricism.etocrm.identity;

import java.util.Set;

/** Role-to-permission policy. Roles grant only the permissions declared here. */
public enum SecurityRole {
    BUSINESS_DEVELOPMENT(Set.of(Permissions.CRM_READ, Permissions.CRM_WRITE)),
    TENANT_ADMIN(Set.of(Permissions.CRM_READ, Permissions.CRM_WRITE, Permissions.TENANT_ADMINISTER)),
    SUPPORT(Set.of(Permissions.SUPPORT_DIAGNOSE)),
    PLATFORM_OPERATOR(Set.of(Permissions.PLATFORM_OPERATE));

    private final Set<String> permissions;

    SecurityRole(Set<String> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<String> permissions() {
        return permissions;
    }
}
