package com.brandempiricism.etocrm.identity;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

/** Identity-provider-neutral application API for tenant membership decisions. */
@Service
public class IdentityApplicationApi {
    private final JdbcTemplate database;

    public IdentityApplicationApi(@Qualifier("platformJdbcTemplate") JdbcTemplate database) {
        this.database = database;
    }

    @Transactional("platformTransactionManager")
    @PreAuthorize("hasAuthority('platform:operate')")
    public void assignInitialAdministrator(UUID tenantId, String actorId) {
        requireActor(actorId);
        ensureIdentity(actorId);
        if (membershipExists(actorId, tenantId)) {
            database.update("update tenant_membership set role = ?, status = ?, updated_at = ? where identity_id = ? and tenant_id = ?",
                CompanyRole.TENANT_ADMIN.name(), MembershipStatus.ACTIVE.name(), Instant.now(), actorId, tenantId);
        } else {
            var now = Instant.now();
            database.update("insert into tenant_membership(identity_id, tenant_id, role, status, created_at, updated_at) values (?, ?, ?, ?, ?, ?)",
                actorId, tenantId, CompanyRole.TENANT_ADMIN.name(), MembershipStatus.ACTIVE.name(), now, now);
        }
        audit(actorId, tenantId, "tenant_membership.initial_administrator_assigned", actorId,
            "Initial tenant administrator membership assigned");
    }

    @Transactional("platformTransactionManager")
    @PreAuthorize("isAuthenticated()")
    public TenantContext selectTenant(String actorId, UUID tenantId) {
        requireActor(actorId);
        var memberships = database.query(
            "select role, status from tenant_membership where identity_id = ? and tenant_id = ?",
            (result, row) -> new Membership(CompanyRole.valueOf(result.getString("role")),
                MembershipStatus.valueOf(result.getString("status"))), actorId, tenantId);
        if (memberships.isEmpty() || memberships.getFirst().status() != MembershipStatus.ACTIVE) {
            throw new TenantAccessDeniedException("An active company membership is required.");
        }
        audit(actorId, tenantId, "tenant_membership.tenant_selected", actorId, "Active tenant selected");
        return new TenantContext(actorId, tenantId, memberships.getFirst().role());
    }

    @Transactional("platformTransactionManager")
    @PreAuthorize("hasAuthority('tenant:administer')")
    public void changeRole(String actorId, String memberId, UUID tenantId, CompanyRole role) {
        requireActor(actorId);
        int changed = database.update(
            "update tenant_membership set role = ?, updated_at = ? where identity_id = ? and tenant_id = ?",
            role.name(), Instant.now(), memberId, tenantId);
        if (changed == 0) throw new IllegalArgumentException("Tenant membership does not exist.");
        audit(actorId, tenantId, "tenant_membership.role_changed", memberId, "Company role changed to " + role.name());
    }

    @Transactional("platformTransactionManager")
    @PreAuthorize("hasAuthority('tenant:administer')")
    public void disableMembership(String actorId, String memberId, UUID tenantId) {
        requireActor(actorId);
        int changed = database.update(
            "update tenant_membership set status = ?, updated_at = ? where identity_id = ? and tenant_id = ?",
            MembershipStatus.DISABLED.name(), Instant.now(), memberId, tenantId);
        if (changed == 0) throw new IllegalArgumentException("Tenant membership does not exist.");
        audit(actorId, tenantId, "tenant_membership.disabled", memberId, "Tenant membership disabled");
    }

    private void ensureIdentity(String actorId) {
        Integer count = database.queryForObject("select count(*) from platform_identity where id = ?", Integer.class, actorId);
        if (count != null && count == 0) {
            database.update("insert into platform_identity(id, created_at) values (?, ?)", actorId, Instant.now());
        }
    }

    private boolean membershipExists(String actorId, UUID tenantId) {
        Integer count = database.queryForObject(
            "select count(*) from tenant_membership where identity_id = ? and tenant_id = ?", Integer.class, actorId, tenantId);
        return count != null && count > 0;
    }

    private void audit(String actorId, UUID tenantId, String action, String aggregateId, String summary) {
        database.update("insert into identity_audit_record(id, actor_id, tenant_id, action, aggregate_type, aggregate_id, change_summary, occurred_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), actorId, tenantId, action, "TENANT_MEMBERSHIP", aggregateId, summary, Instant.now());
    }

    private static void requireActor(String actorId) {
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("Actor identity is required.");
    }

    private record Membership(CompanyRole role, MembershipStatus status) {}
    public record TenantContext(String actorId, UUID tenantId, CompanyRole role) {}
    public enum CompanyRole { TENANT_ADMIN, BUSINESS_DEVELOPMENT }
    enum MembershipStatus { ACTIVE, DISABLED }
}
