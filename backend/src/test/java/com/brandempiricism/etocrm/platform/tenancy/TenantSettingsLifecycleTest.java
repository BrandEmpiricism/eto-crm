package com.brandempiricism.etocrm.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.brandempiricism.etocrm.commons.ServiceUnavailableException;
import com.brandempiricism.etocrm.identity.IdentityApplicationApi;
import com.brandempiricism.etocrm.identity.Permissions;
import com.brandempiricism.etocrm.identity.TenantContextHolder;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "eto.platform.datasource.url=jdbc:h2:mem:settings_acceptance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class TenantSettingsLifecycleTest {
    private static final UUID TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    @Autowired TenantSettingsService settings;
    @Autowired TenantLifecycleService lifecycle;
    @Autowired IdentityApplicationApi identities;
    @Autowired @Qualifier("platformJdbcTemplate") JdbcTemplate database;
    @MockitoBean TenantClosureInfrastructure infrastructure;

    @BeforeEach void setup() {
        authenticate(Permissions.PLATFORM_OPERATE, Permissions.TENANT_ADMINISTER, Permissions.CRM_READ);
        database.update("update tenant_registry set status='ACTIVE' where id=?", TENANT);
        identities.assignInitialAdministrator(TENANT, "settings-admin");
        TenantContextHolder.bind(identities.selectTenant("settings-admin", TENANT));
    }
    @AfterEach void cleanup() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        database.update("delete from tenant_settings");
        database.update("delete from tenant_lifecycle_record");
        database.update("delete from platform_tenant_audit");
    }

    @Test void settingsUseSimpleDefaultsAndPersistOnlyInPlatform() {
        assertThat(settings.get().timezone()).isEqualTo("UTC");
        var changed = settings.update(new TenantSettingsService.UpdateSettings("R Hyper", "en-CA", "America/Toronto", "#123456"));
        assertThat(changed.displayName()).isEqualTo("R Hyper");
        assertThat(changed.timezone()).isEqualTo("America/Toronto");
        assertThat(database.queryForObject("select count(*) from platform_tenant_audit where action='tenant.settings.updated'", Integer.class)).isEqualTo(1);
    }

    @Test void unsafeBrandingAndInvalidRegionalSettingsDoNotChangeState() {
        for (var input : List.of(
                new TenantSettingsService.UpdateSettings("<script>bad</script>", "en", "UTC", null),
                new TenantSettingsService.UpdateSettings("Company", "en", "Invalid/Zone", null),
                new TenantSettingsService.UpdateSettings("Company", "en", "UTC", "url(javascript:bad)"))) {
            assertThatThrownBy(() -> settings.update(input)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(database.queryForObject("select count(*) from tenant_settings", Integer.class)).isZero();
    }

    @Test void nonAdministratorCannotChangeSettingsOrSuspendCompany() {
        authenticate(Permissions.CRM_READ);
        assertThatThrownBy(() -> settings.update(new TenantSettingsService.UpdateSettings("Company", "en", "UTC", null)))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> lifecycle.suspend(TENANT)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void suspensionPreventsNewMembershipSelectionWithoutDeletingData() {
        lifecycle.suspend(TENANT);
        assertThatThrownBy(() -> identities.selectTenant("settings-admin", TENANT))
            .isInstanceOf(com.brandempiricism.etocrm.identity.TenantAccessDeniedException.class);
        assertThat(database.queryForObject("select count(*) from tenant_membership where tenant_id=?", Integer.class, TENANT)).isPositive();
    }

    @Test void closureRequiresBackupConfirmationAndRevocationBeforeThirtyDayRetentionBegins() {
        assertThatThrownBy(() -> lifecycle.close(TENANT, "https://sensitive-backup-url"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(infrastructure, never()).revokeCredentials(TENANT);
        var closed = lifecycle.close(TENANT, "backup-confirmed-001");
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(Duration.between(closed.closedAt(), closed.retainUntil())).isEqualTo(Duration.ofDays(30));
        assertThat(closed.deletionApprovedAt()).isNull();
        assertThatThrownBy(() -> lifecycle.approveDeletion(TENANT)).isInstanceOf(IllegalArgumentException.class);
        verify(infrastructure).revokeCredentials(TENANT);
    }

    @Test void failedRevocationLeavesTenantSuspendedAndCanBeRetried() {
        doThrow(new ServiceUnavailableException("Revocation unavailable")).doNothing().when(infrastructure).revokeCredentials(TENANT);
        assertThatThrownBy(() -> lifecycle.close(TENANT, "backup-001")).isInstanceOf(ServiceUnavailableException.class);
        assertThat(database.queryForObject("select status from tenant_registry where id=?", String.class, TENANT)).isEqualTo("SUSPENDED");
        assertThat(database.queryForObject("select count(*) from tenant_lifecycle_record", Integer.class)).isZero();
        assertThat(lifecycle.close(TENANT, "backup-001").status()).isEqualTo("CLOSED");
    }

    @Test void retentionExpiryDoesNotItselfApproveDeletion() {
        lifecycle.close(TENANT, "backup-001");
        database.update("update tenant_lifecycle_record set retain_until=? where tenant_id=?",
            java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)), TENANT);
        assertThat(database.queryForObject("select deletion_approved_at from tenant_lifecycle_record where tenant_id=?", java.sql.Timestamp.class, TENANT)).isNull();
        assertThat(lifecycle.approveDeletion(TENANT).deletionApprovedAt()).isNotNull();
        assertThat(database.queryForObject("select status from tenant_registry where id=?", String.class, TENANT)).isEqualTo("CLOSED");
    }

    private static void authenticate(String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("settings-admin", null,
            java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }
}
