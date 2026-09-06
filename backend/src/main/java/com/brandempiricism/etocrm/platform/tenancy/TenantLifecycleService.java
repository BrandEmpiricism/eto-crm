package com.brandempiricism.etocrm.platform.tenancy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TenantLifecycleService {
    private final JdbcTemplate database;
    private final TransactionTemplate transactions;
    private final TenantClosureInfrastructure infrastructure;

    TenantLifecycleService(@Qualifier("platformJdbcTemplate") JdbcTemplate database,
            @Qualifier("platformTransactionManager") PlatformTransactionManager manager,
            TenantClosureInfrastructure infrastructure) {
        this.database = database;
        this.transactions = new TransactionTemplate(manager);
        this.infrastructure = infrastructure;
    }

    @PreAuthorize("hasAuthority('platform:operate')")
    public Lifecycle suspend(UUID tenant) {
        return transactions.execute(transaction -> {
            var status = lockStatus(tenant);
            if (status.equals("CLOSED")) return view(tenant);
            if (!status.equals("ACTIVE") && !status.equals("SUSPENDED")) throw new IllegalArgumentException("Only an active tenant can be suspended.");
            if (status.equals("ACTIVE")) {
                database.update("update tenant_registry set status='SUSPENDED',updated_at=?,updated_by=? where id=?",
                    Timestamp.from(Instant.now()), actor(), tenant);
                audit(tenant, "tenant.suspended", "New tenant requests and jobs blocked");
            }
            return view(tenant);
        });
    }

    @PreAuthorize("hasAuthority('platform:operate')")
    public Lifecycle close(UUID tenant, String exportBackupReference) {
        if (exportBackupReference == null || !exportBackupReference.matches("[A-Za-z0-9_-]{1,200}")) {
            throw new IllegalArgumentException("An opaque operator-confirmed export/backup reference is required.");
        }
        var suspended = suspend(tenant);
        if (suspended.status().equals("CLOSED")) return suspended;
        // Suspension is committed before calling infrastructure. Failure cannot reopen tenant access.
        infrastructure.revokeCredentials(tenant);
        return transactions.execute(transaction -> {
            if (lockStatus(tenant).equals("CLOSED")) return view(tenant);
            var now = Instant.now();
            database.update("insert into tenant_lifecycle_record(tenant_id,closed_at,retain_until,export_backup_reference) values (?,?,?,?)",
                tenant, Timestamp.from(now), Timestamp.from(now.plus(Duration.ofDays(30))), exportBackupReference);
            database.update("update tenant_registry set status='CLOSED',updated_at=?,updated_by=? where id=?", Timestamp.from(now), actor(), tenant);
            audit(tenant, "tenant.closed", "Credentials revoked; export/backup confirmed; data retained for 30 days");
            return view(tenant);
        });
    }

    @PreAuthorize("hasAuthority('platform:operate')")
    public Lifecycle approveDeletion(UUID tenant) {
        return transactions.execute(transaction -> {
            if (!lockStatus(tenant).equals("CLOSED")) throw new IllegalArgumentException("Only a closed tenant can be approved for deletion.");
            var lifecycle = view(tenant);
            if (Instant.now().isBefore(lifecycle.retainUntil())) throw new IllegalArgumentException("The 30-day retention period has not elapsed.");
            int changed = database.update("update tenant_lifecycle_record set deletion_approved_at=?,deletion_approved_by=? "
                + "where tenant_id=? and deletion_approved_at is null", Timestamp.from(Instant.now()), actor(), tenant);
            if (changed != 0) audit(tenant, "tenant.deletion.approved", "Operator approved deletion after retention; execution requires controlled infrastructure");
            return view(tenant);
        });
    }

    private String lockStatus(UUID tenant) {
        var states = database.queryForList("select status from tenant_registry where id=? for update", String.class, tenant);
        if (states.size() != 1) throw new IllegalArgumentException("Client tenant does not exist.");
        return states.getFirst();
    }

    private Lifecycle view(UUID tenant) {
        return database.queryForObject("select t.id,t.status,l.closed_at,l.retain_until,l.deletion_approved_at from tenant_registry t "
            + "left join tenant_lifecycle_record l on l.tenant_id=t.id where t.id=?",
            (row, index) -> new Lifecycle(row.getObject("id", UUID.class), row.getString("status"),
                instant(row.getTimestamp("closed_at")), instant(row.getTimestamp("retain_until")), instant(row.getTimestamp("deletion_approved_at"))), tenant);
    }

    private void audit(UUID tenant, String action, String summary) {
        database.update("insert into platform_tenant_audit(id,tenant_id,actor_id,action,occurred_at,request_id,summary) values (?,?,?,?,?,?,?)",
            UUID.randomUUID(), tenant, actor(), action, Timestamp.from(Instant.now()), MDC.get("requestId"), summary);
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static String actor() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    public record Lifecycle(UUID tenantId, String status, Instant closedAt, Instant retainUntil, Instant deletionApprovedAt) {}
}
