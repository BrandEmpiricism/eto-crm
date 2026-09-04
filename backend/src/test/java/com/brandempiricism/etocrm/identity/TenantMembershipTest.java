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

@SpringBootTest
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
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
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

        assertThatThrownBy(() -> identities.selectTenant("disabled-member", TENANT_ID))
            .isInstanceOf(TenantAccessDeniedException.class)
            .hasMessage("An active company membership is required.");
    }

    @Test
    void roleChangesAreExplicitAndAudited() {
        identities.assignInitialAdministrator(TENANT_ID, "role-member");

        identities.changeRole("membership-admin", "role-member", TENANT_ID,
            IdentityApplicationApi.CompanyRole.BUSINESS_DEVELOPMENT);

        assertThat(identities.selectTenant("role-member", TENANT_ID).role())
            .isEqualTo(IdentityApplicationApi.CompanyRole.BUSINESS_DEVELOPMENT);
        assertThat(auditCount("membership-admin", "tenant_membership.role_changed")).isEqualTo(1);
    }

    private int auditCount(String actorId, String action) {
        return database.queryForObject(
            "select count(*) from identity_audit_record where actor_id = ? and action = ?",
            Integer.class, actorId, action);
    }
}
