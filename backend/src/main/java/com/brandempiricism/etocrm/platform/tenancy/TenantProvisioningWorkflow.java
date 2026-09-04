package com.brandempiricism.etocrm.platform.tenancy;

import static com.brandempiricism.etocrm.platform.tenancy.ProvisioningStep.*;

import java.time.Instant;
import java.util.UUID;
import com.brandempiricism.etocrm.commons.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class TenantProvisioningWorkflow {
    private static final Logger LOG = LoggerFactory.getLogger(TenantProvisioningWorkflow.class);

    private final TenantRegistryRepository tenants;
    private final TenantProvisioningInfrastructure infrastructure;
    private final TenantAdministratorProvisioner administrators;
    private final TransactionTemplate transactions;

    TenantProvisioningWorkflow(TenantRegistryRepository tenants,
                               TenantProvisioningInfrastructure infrastructure,
                               TenantAdministratorProvisioner administrators,
                               @Qualifier("platformTransactionManager") org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.tenants = tenants;
        this.infrastructure = infrastructure;
        this.administrators = administrators;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    TenantProvisioningService.TenantView provision(UUID tenantId, String actorId) {
        try {
            var tenant = load(tenantId);
            if (tenant.status == TenantStatus.ACTIVE) return TenantProvisioningService.view(tenant);

            if (tenant.provisioningStep == REGISTERED) {
                var allocation = infrastructure.allocateDatabase(spec(tenant));
                tenant = advance(tenantId, DATABASE_ALLOCATED, allocation.credentialSecretRef(), actorId);
            }
            if (tenant.provisioningStep == DATABASE_ALLOCATED) {
                infrastructure.migrate(spec(tenant), requiredSecret(tenant));
                tenant = advance(tenantId, MIGRATED, null, actorId);
            }
            if (tenant.provisioningStep == MIGRATED) {
                infrastructure.seedDefaults(spec(tenant), requiredSecret(tenant));
                tenant = advance(tenantId, DEFAULTS_SEEDED, null, actorId);
            }
            if (tenant.provisioningStep == DEFAULTS_SEEDED) {
                infrastructure.verify(spec(tenant), requiredSecret(tenant));
                tenant = advance(tenantId, VERIFIED, null, actorId);
            }
            if (tenant.provisioningStep == VERIFIED) {
                administrators.assignInitialAdministrator(tenant.id, actorId);
                tenant = advance(tenantId, ADMIN_ASSIGNED, null, actorId);
            }
            if (tenant.provisioningStep == ADMIN_ASSIGNED) {
                tenant = activate(tenantId, actorId);
            }
            return TenantProvisioningService.view(tenant);
        } catch (RuntimeException failure) {
            recordFailure(tenantId, failure, actorId);
            throw failure;
        }
    }

    private TenantRegistryEntity load(UUID tenantId) {
        return tenants.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Client tenant does not exist."));
    }

    private TenantRegistryEntity advance(UUID id, ProvisioningStep step, String secretRef, String actor) {
        return transactions.execute(status -> {
            var tenant = load(id);
            tenant.provisioningStep = step;
            if (secretRef != null) tenant.credentialSecretRef = secretRef;
            tenant.failureCode = null;
            touch(tenant, actor);
            log("tenant.provisioning.step_completed", tenant, step.name());
            return tenants.save(tenant);
        });
    }

    private TenantRegistryEntity activate(UUID id, String actor) {
        return transactions.execute(status -> {
            var tenant = load(id);
            tenant.provisioningStep = COMPLETE;
            tenant.status = TenantStatus.ACTIVE;
            tenant.failureCode = null;
            touch(tenant, actor);
            log("tenant.provisioning.activated", tenant, COMPLETE.name());
            return tenants.save(tenant);
        });
    }

    private void recordFailure(UUID id, RuntimeException failure, String actor) {
        transactions.executeWithoutResult(status -> tenants.findById(id).ifPresent(tenant -> {
            tenant.failureCode = failure instanceof ServiceUnavailableException
                ? "INFRASTRUCTURE_UNAVAILABLE" : "STEP_FAILED";
            touch(tenant, actor);
            tenants.save(tenant);
            LOG.atWarn().addKeyValue("event.name", "tenant.provisioning.failed")
                .addKeyValue("tenantId", tenant.id)
                .addKeyValue("provisioningStep", tenant.provisioningStep)
                .addKeyValue("failureCode", tenant.failureCode)
                .log("Tenant provisioning step failed");
        }));
    }

    private static TenantProvisioningInfrastructure.TenantDatabaseSpec spec(TenantRegistryEntity tenant) {
        return new TenantProvisioningInfrastructure.TenantDatabaseSpec(tenant.id, tenant.slug, tenant.databaseName);
    }

    private static String requiredSecret(TenantRegistryEntity tenant) {
        if (tenant.credentialSecretRef == null || tenant.credentialSecretRef.isBlank()) {
            throw new IllegalStateException("Tenant credential secret reference is missing.");
        }
        return tenant.credentialSecretRef;
    }

    private static void touch(TenantRegistryEntity tenant, String actor) {
        tenant.updatedAt = Instant.now();
        tenant.updatedBy = actor;
    }

    private static void log(String event, TenantRegistryEntity tenant, String step) {
        LOG.atInfo().addKeyValue("event.name", event)
            .addKeyValue("tenantId", tenant.id)
            .addKeyValue("provisioningStep", step)
            .log("Tenant provisioning advanced");
    }
}
