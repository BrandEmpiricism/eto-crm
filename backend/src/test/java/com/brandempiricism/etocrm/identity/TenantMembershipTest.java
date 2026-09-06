package com.brandempiricism.etocrm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(properties = "eto.platform.datasource.url=jdbc:h2:mem:membership_acceptance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class TenantMembershipTest {
    private static final UUID TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired IdentityApplicationApi identities;
    @Autowired @Qualifier("platformJdbcTemplate") JdbcTemplate database;

    @BeforeEach
    void authorizeMembershipAdministration() {
        var authorities = java.util.List.of(
            new SimpleGrantedAuthority(Permissions.PLATFORM_OPERATE),
            new SimpleGrantedAuthority(Permissions.TENANT_ADMINISTER));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("membership-admin", null, authorities));
        database.update("update tenant_registry set status = 'ACTIVE' where id = ?", TENANT_ID);
        identities.assignInitialAdministrator(TENANT_ID, "membership-admin");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        database.update("delete from identity_audit_record");
        database.update("delete from tenant_membership");
        database.update("delete from platform_identity");
        database.update("update tenant_registry set status = 'PROVISIONING' where id = ?", TENANT_ID);
    }

    @Test
    void activeMembershipSelectsExactlyOneTenantAndRecordsTheDecision() {
        identities.assignInitialAdministrator(TENANT_ID, "membership-admin");

        var context = identities.selectTenant("membership-admin", TENANT_ID);

        assertThat(context.actorId()).isEqualTo("membership-admin");
        assertThat(context.tenantId()).isEqualTo(TENANT_ID);
        assertThat(context.role()).isEqualTo(IdentityApplicationApi.CompanyRole.TENANT_ADMIN);
        assertThat(auditCount("membership-admin", "tenant_membership.tenant_selected")).isEqualTo(1);
    }

    @Test
    void disabledMembershipFailsClosed() {
        identities.assignInitialAdministrator(TENANT_ID, "disabled-member");
        identities.disableMembership("membership-admin", "disabled-member", TENANT_ID);
        authenticate("disabled-member");

        assertThatThrownBy(() -> identities.selectTenant("disabled-member", TENANT_ID))
            .isInstanceOf(TenantAccessDeniedException.class)
            .hasMessage("An active company membership is required.");
    }

    @Test
    void roleChangesAreExplicitAndAudited() {
        identities.assignInitialAdministrator(TENANT_ID, "role-member");

        identities.changeRole("membership-admin", "role-member", TENANT_ID,
            IdentityApplicationApi.CompanyRole.BUSINESS_DEVELOPMENT);

        authenticate("role-member");
        assertThat(identities.selectTenant("role-member", TENANT_ID).role())
            .isEqualTo(IdentityApplicationApi.CompanyRole.BUSINESS_DEVELOPMENT);
        assertThat(auditCount("membership-admin", "tenant_membership.role_changed")).isEqualTo(1);
    }

    @Test
    void tenantServiceIdentityRequiresMembershipAndRecordsJobAuthorization() {
        identities.assignInitialAdministrator(TENANT_ID, "service:outbox-test");
        authenticate("service:outbox-test");

        var context = identities.selectTenantForService("service:outbox-test", TENANT_ID);

        assertThat(context.actorId()).isEqualTo("service:outbox-test");
        assertThat(auditCount("service:outbox-test", "tenant_job.context_authorized")).isEqualTo(1);
    }

    @Test
    void ordinaryIdentityCannotBeUsedAsABackgroundService() {
        identities.assignInitialAdministrator(TENANT_ID, "ordinary-user");
        authenticate("ordinary-user");
        assertThatThrownBy(() -> identities.selectTenantForService("ordinary-user", TENANT_ID))
            .isInstanceOf(TenantAccessDeniedException.class)
            .hasMessage("An authorized tenant service identity is required.");
    }

    @Test
    void authenticatedUserCannotSelectAnotherMembersIdentity() {
        identities.assignInitialAdministrator(TENANT_ID, "other-member");
        assertThatThrownBy(() -> identities.selectTenant("other-member", TENANT_ID))
            .isInstanceOf(TenantAccessDeniedException.class);
        assertThat(auditCount("other-member", "tenant_membership.tenant_selected")).isZero();
    }

    @Test
    void broadPermissionDoesNotAuthorizeAdministrationOfAnotherTenant() {
        identities.assignInitialAdministrator(TENANT_ID, "target-member");
        authenticate("unrelated-administrator");
        assertThatThrownBy(() -> identities.changeRole("unrelated-administrator", "target-member", TENANT_ID,
            IdentityApplicationApi.CompanyRole.BUSINESS_DEVELOPMENT)).isInstanceOf(TenantAccessDeniedException.class);
        assertThatThrownBy(() -> identities.disableMembership("unrelated-administrator", "target-member", TENANT_ID))
            .isInstanceOf(TenantAccessDeniedException.class);
        assertThat(database.queryForObject("select status from tenant_membership where identity_id = ? and tenant_id = ?",
            String.class, "target-member", TENANT_ID)).isEqualTo("ACTIVE");
        assertThat(auditCount("unrelated-administrator", "tenant_membership.role_changed")).isZero();
    }

    @Test
    void administratorCannotForgeAuditActorOrSwitchBoundTenant() {
        assertThatThrownBy(() -> identities.disableMembership("forged-actor", "membership-admin", TENANT_ID))
            .isInstanceOf(TenantAccessDeniedException.class);
        TenantContextHolder.bind(new IdentityApplicationApi.TenantContext("membership-admin", UUID.randomUUID(),
            IdentityApplicationApi.CompanyRole.TENANT_ADMIN));
        assertThatThrownBy(() -> identities.disableMembership("membership-admin", "membership-admin", TENANT_ID))
            .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void initialAdministratorAuditIdentifiesOperatorAndBeneficiarySeparately() {
        identities.assignInitialAdministrator(TENANT_ID, "new-administrator");
        assertThat(database.queryForObject("select actor_id from identity_audit_record where aggregate_id = ?",
            String.class, "new-administrator")).isEqualTo("membership-admin");
    }

    @Test
    void callerCannotImpersonateAnAuthorizedBackgroundService() {
        identities.assignInitialAdministrator(TENANT_ID, "service:protected");
        assertThatThrownBy(() -> identities.selectTenantForService("service:protected", TENANT_ID))
            .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void disabledAdministratorCannotKeepUsingPreviouslyGrantedAuthority() {
        identities.assignInitialAdministrator(TENANT_ID, "managed-member");
        identities.disableMembership("membership-admin", "membership-admin", TENANT_ID);
        assertThatThrownBy(() -> identities.changeRole("membership-admin", "managed-member", TENANT_ID,
            IdentityApplicationApi.CompanyRole.BUSINESS_DEVELOPMENT)).isInstanceOf(TenantAccessDeniedException.class);
    }

    private static void authenticate(String actor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
            java.util.List.of(new SimpleGrantedAuthority(Permissions.TENANT_ADMINISTER))));
    }

    private int auditCount(String actorId, String action) {
        return database.queryForObject(
            "select count(*) from identity_audit_record where actor_id = ? and action = ?",
            Integer.class, actorId, action);
    }
}
