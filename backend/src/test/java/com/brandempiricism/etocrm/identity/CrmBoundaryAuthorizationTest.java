package com.brandempiricism.etocrm.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class CrmBoundaryAuthorizationTest {
    @Autowired ApplicationContext application;
    private static final UUID ID = UUID.randomUUID();

    // Invoke Spring service proxies directly: HTTP rules must not be the only protection.
    @TestFactory
    Stream<DynamicTest> supportAndPlatformOperatorsCannotReadOrChangeCrm() {
        return Stream.of(SecurityRole.SUPPORT, SecurityRole.PLATFORM_OPERATOR).flatMap(role ->
            boundaries().stream().map(boundary -> DynamicTest.dynamicTest(role + " cannot " + boundary.description(), () -> {
                authenticate(role);
                try {
                    assertThatThrownBy(() -> invoke(boundary)).isInstanceOf(AccessDeniedException.class);
                } finally { SecurityContextHolder.clearContext(); }
            })));
    }

    @TestFactory
    Stream<DynamicTest> businessWritersCannotForgeTheRecordedActor() {
        return boundaries().stream().filter(boundary -> Arrays.asList(boundary.arguments()).contains("forged-actor"))
            .map(boundary -> DynamicTest.dynamicTest("Cannot forge actor when attempting to " + boundary.description(), () -> {
                authenticate(SecurityRole.BUSINESS_DEVELOPMENT);
                try {
                    assertThatThrownBy(() -> invoke(boundary)).isInstanceOf(AccessDeniedException.class);
                } finally { SecurityContextHolder.clearContext(); }
            }));
    }

    private void invoke(Boundary boundary) throws Throwable {
        var bean = application.getBean(boundary.bean());
        var method = Arrays.stream(AopUtils.getTargetClass(bean).getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(boundary.method())).findFirst().orElseThrow();
        method.setAccessible(true);
        try { method.invoke(bean, boundary.arguments()); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    @TestFactory
    Stream<DynamicTest> platformProvisioningCannotForgeTheOperator() {
        return Stream.of(
            new Boundary("register a company", "tenantProvisioningService", "register", new Object[] {null, "forged-actor"}),
            new Boundary("provision a company", "tenantProvisioningWorkflow", "provision", new Object[] {ID, "forged-actor"})
        ).map(boundary -> DynamicTest.dynamicTest(boundary.description() + " requires the authenticated operator", () -> {
            authenticate(SecurityRole.PLATFORM_OPERATOR);
            try { assertThatThrownBy(() -> invoke(boundary)).isInstanceOf(AccessDeniedException.class); }
            finally { SecurityContextHolder.clearContext(); }
        }));
    }

    private static void authenticate(SecurityRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("boundary-user", null,
            role.permissions().stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private static List<Boundary> boundaries() {
        return List.of(
            new Boundary("read an account", "accountService", "getAccount", new Object[] {ID}),
            new Boundary("list accounts", "accountService", "list", new Object[] {}),
            new Boundary("create an account", "accountService", "createAccount", new Object[] {null, "forged-actor"}),
            new Boundary("list contacts", "contactService", "list", new Object[] {ID, null}),
            new Boundary("read a contact", "contactService", "get", new Object[] {ID, ID}),
            new Boundary("add a contact", "contactService", "create", new Object[] {ID, null, "forged-actor"}),
            new Boundary("edit a contact", "contactService", "update", new Object[] {ID, ID, null, "forged-actor"}),
            new Boundary("read a capability", "capabilityService", "get", new Object[] {ID}),
            new Boundary("list capabilities", "capabilityService", "list", new Object[] {}),
            new Boundary("read a signal", "signalService", "get", new Object[] {ID, ID}),
            new Boundary("list signals", "signalService", "list", new Object[] {ID}),
            new Boundary("record a signal", "signalService", "create", new Object[] {ID, null, "forged-actor", false}),
            new Boundary("read a match", "matchService", "get", new Object[] {ID, ID}),
            new Boundary("look up a match for another module", "matchService", "getMatch", new Object[] {ID}),
            new Boundary("list matches", "matchService", "list", new Object[] {ID}),
            new Boundary("list related matches", "matchService", "listForSignal", new Object[] {ID, ID}),
            new Boundary("read an owner queue", "matchService", "queue", new Object[] {"owner"}),
            new Boundary("create an account match", "matchService", "createForAccount", new Object[] {ID, null, "forged-actor"}),
            new Boundary("create the walking slice", "matchService", "createWalkingSlice", new Object[] {null, "forged-actor"}),
            new Boundary("list next actions", "nextActionService", "grouped", new Object[] {ID}),
            new Boundary("add a next action", "nextActionService", "create", new Object[] {ID, null, "forged-actor"}),
            new Boundary("complete a next action", "nextActionService", "complete", new Object[] {ID, ID, "forged-actor"}),
            new Boundary("reschedule a next action", "nextActionService", "reschedule", new Object[] {ID, ID, null, "forged-actor"})
        );
    }

    private record Boundary(String description, String bean, String method, Object[] arguments) {}
}
