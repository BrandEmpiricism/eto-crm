package com.brandempiricism.etocrm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brandempiricism.etocrm.accounts.AccountApplicationApi;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class ApplicationAuthorizationTest {
    @Autowired AccountApplicationApi accounts;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rolePermissionMappingIsCentralAndLeastPrivilege() {
        assertThat(SecurityRole.BUSINESS_DEVELOPMENT.permissions())
            .containsExactlyInAnyOrder(Permissions.CRM_READ, Permissions.CRM_WRITE);
        assertThat(SecurityRole.SUPPORT.permissions())
            .containsExactly(Permissions.SUPPORT_DIAGNOSE)
            .doesNotContain(Permissions.CRM_WRITE, Permissions.PLATFORM_OPERATE);
        assertThat(SecurityRole.PLATFORM_OPERATOR.permissions())
            .containsExactly(Permissions.PLATFORM_OPERATE)
            .doesNotContain(Permissions.CRM_WRITE);
    }

    @Test
    void supportRoleCannotCallStateChangingApplicationApi() {
        authenticate(SecurityRole.SUPPORT);
        var command = new AccountApplicationApi.CreateAccount(
            "Denied Tools", "Manufacturing", "Ontario", null, null, null, List.of());

        assertThatThrownBy(() -> accounts.createAccount(command, "support-user"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void businessDevelopmentRoleCanCallStateChangingApplicationApi() {
        authenticate(SecurityRole.BUSINESS_DEVELOPMENT);
        var command = new AccountApplicationApi.CreateAccount(
            "Authorized Tools", "Manufacturing", "Ontario", null, null, null, List.of());

        assertThat(accounts.createAccount(command, "bd-user").name()).isEqualTo("Authorized Tools");
    }

    private static void authenticate(SecurityRole role) {
        var authorities = role.permissions().stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("test-user", null, authorities));
    }
}
