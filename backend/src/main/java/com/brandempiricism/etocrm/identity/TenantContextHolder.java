package com.brandempiricism.etocrm.identity;

import java.util.Optional;

/** Request-scoped immutable tenant authorization decision. */
public final class TenantContextHolder {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void bind(IdentityApplicationApi.TenantContext context) {
        if (CURRENT.get() != null) throw new IllegalStateException("Tenant context is already bound.");
        CURRENT.set(new Scope(java.util.Objects.requireNonNull(context)));
    }

    public static Optional<IdentityApplicationApi.TenantContext> current() {
        return Optional.ofNullable(CURRENT.get()).map(scope -> scope.context);
    }

    public static void onClear(Runnable cleanup) {
        var scope = CURRENT.get();
        if (scope == null) throw new IllegalStateException("Verified tenant context is required.");
        scope.cleanup.add(cleanup);
    }

    public static void clear() {
        var scope = CURRENT.get();
        CURRENT.remove();
        if (scope == null) return;
        RuntimeException failure = null;
        for (var cleanup : scope.cleanup) {
            try { cleanup.run(); }
            catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private static final class Scope {
        final IdentityApplicationApi.TenantContext context;
        final java.util.List<Runnable> cleanup = new java.util.ArrayList<>();
        Scope(IdentityApplicationApi.TenantContext context) { this.context = context; }
    }
}
