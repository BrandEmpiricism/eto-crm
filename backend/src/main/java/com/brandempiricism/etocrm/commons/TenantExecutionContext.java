package com.brandempiricism.etocrm.commons;

import java.util.Optional;
import java.util.UUID;

/** Persistence consumes an established authorization decision, never caller-supplied routes. */
public interface TenantExecutionContext {
    Optional<UUID> currentTenantId();
    void onClear(Runnable cleanup);
}
