package com.brandempiricism.etocrm.identity;

import java.util.UUID;
import com.brandempiricism.etocrm.commons.DiagnosticContext;
import com.brandempiricism.etocrm.commons.DiagnosticEvents;
import org.springframework.stereotype.Service;

/** Runs background work with an explicitly authorized and audited service/tenant identity. */
@Service
public class TenantJobExecutor {
    private final IdentityApplicationApi identities;

    public TenantJobExecutor(IdentityApplicationApi identities) {
        this.identities = identities;
    }

    public void run(String serviceId, UUID tenantId, Runnable work) {
        run(serviceId, tenantId, DiagnosticContext.capture(), work);
    }

    public void run(String serviceId, UUID tenantId, DiagnosticContext.Correlation parent, Runnable work) {
        if (TenantContextHolder.current().isPresent()) {
            throw new IllegalStateException("Tenant context is already bound.");
        }
        long started = System.nanoTime();
        try (var scope = DiagnosticContext.start(parent)) {
            try {
                var context = identities.selectTenantForService(serviceId, tenantId);
                TenantContextHolder.bind(context);
                DiagnosticContext.verifiedIdentity(context.actorId(), context.tenantId());
                try { work.run(); } finally { TenantContextHolder.clear(); }
                DiagnosticEvents.finished(DiagnosticEvents.Event.JOB_COMPLETED, DiagnosticEvents.Outcome.success, started, 0, null);
            } catch (org.springframework.security.access.AccessDeniedException denied) {
                DiagnosticEvents.finished(DiagnosticEvents.Event.JOB_COMPLETED, DiagnosticEvents.Outcome.rejected, started, 0, null);
                throw denied;
            } catch (RuntimeException failure) {
                DiagnosticEvents.finished(DiagnosticEvents.Event.JOB_COMPLETED, DiagnosticEvents.Outcome.failure, started, 0, failure);
                throw failure;
            }
        }
    }
}
