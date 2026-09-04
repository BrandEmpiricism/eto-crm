package com.brandempiricism.etocrm.identity;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/** Runs background work with an explicitly authorized and audited service/tenant identity. */
@Service
public class TenantJobExecutor {
    private final IdentityApplicationApi identities;

    public TenantJobExecutor(IdentityApplicationApi identities) {
        this.identities = identities;
    }

    public void run(String serviceId, UUID tenantId, Runnable work) {
        if (TenantContextHolder.current().isPresent()) {
            throw new IllegalStateException("Tenant context is already bound.");
        }
        var context = identities.selectTenantForService(serviceId, tenantId);
        TenantContextHolder.bind(context);
        MDC.put("actorId", serviceId);
        MDC.put("tenantId", tenantId.toString());
        try {
            work.run();
        } finally {
            TenantContextHolder.clear();
            MDC.remove("actorId");
            MDC.remove("tenantId");
        }
    }
}
