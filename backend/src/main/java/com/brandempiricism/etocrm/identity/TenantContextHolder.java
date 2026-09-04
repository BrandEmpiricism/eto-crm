package com.brandempiricism.etocrm.identity;

import java.util.Optional;

/** Request-scoped immutable tenant authorization decision. */
public final class TenantContextHolder {
    private static final ThreadLocal<IdentityApplicationApi.TenantContext> CURRENT = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void bind(IdentityApplicationApi.TenantContext context) {
        if (CURRENT.get() != null) throw new IllegalStateException("Tenant context is already bound.");
        CURRENT.set(context);
    }

    public static Optional<IdentityApplicationApi.TenantContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
