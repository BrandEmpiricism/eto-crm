package com.brandempiricism.etocrm.identity;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "eto.security.mode=oidc")
@AutoConfigureMockMvc
class OidcSecurityTest {
    private static final UUID TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("platformJdbcTemplate") JdbcTemplate database;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void activeMembership() {
        database.update("update tenant_registry set status = 'ACTIVE' where id = ?", TENANT_ID);
        database.update("insert into platform_identity(id, created_at) values (?, ?)", "oidc-user", Instant.now());
        database.update("insert into tenant_membership(identity_id, tenant_id, role, status, created_at, updated_at) values (?, ?, 'BUSINESS_DEVELOPMENT', 'ACTIVE', ?, ?)",
            "oidc-user", TENANT_ID, Instant.now(), Instant.now());
    }

    @AfterEach
    void restoreFixture() {
        database.update("delete from identity_audit_record where actor_id = ?", "oidc-user");
        database.update("delete from tenant_membership where identity_id = ?", "oidc-user");
        database.update("delete from platform_identity where id = ?", "oidc-user");
        database.update("update tenant_registry set status = 'PROVISIONING' where id = ?", TENANT_ID);
    }

    @Test
    void verifiedSubjectAndKnownRoleAuthorizeAWrite() throws Exception {
        var now = Instant.now();
        when(decoder.decode("valid-token")).thenReturn(Jwt.withTokenValue("valid-token")
            .header("alg", "RS256").subject("oidc-user").issuedAt(now).expiresAt(now.plusSeconds(300))
            .claim("roles", List.of("BUSINESS_DEVELOPMENT")).build());

        mvc.perform(post("/api/accounts")
                .header("Authorization", "Bearer valid-token")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"OIDC Tools","industry":"Manufacturing","location":"Ontario"}
                    """))
            .andExpect(status().isCreated());
    }

    @Test
    void invalidBearerTokenReturnsSafeProblemDetails() throws Exception {
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("signature detail must not escape"));

        mvc.perform(post("/api/accounts")
                .header("Authorization", "Bearer invalid-token")
                .header("X-Request-Id", "oidc-failure")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Authentication failed"))
            .andExpect(jsonPath("$.detail").value("A valid bearer token is required."))
            .andExpect(jsonPath("$.requestId").value("oidc-failure"));
    }

    @Test
    void actorHeaderCannotAuthenticateInOidcMode() throws Exception {
        mvc.perform(post("/api/accounts").header("X-Actor", "forged-user")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutRequestedMembershipFailsClosed() throws Exception {
        stubValidToken();
        var unrelatedTenant = UUID.randomUUID();

        mvc.perform(post("/api/accounts")
                .header("Authorization", "Bearer valid-token")
                .header("X-Tenant-Id", unrelatedTenant)
                .header("X-Request-Id", "tenant-denied")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Tenant access denied"))
            .andExpect(jsonPath("$.requestId").value("tenant-denied"));
    }

    @Test
    void disabledMembershipCannotReachTenantApplicationServices() throws Exception {
        stubValidToken();
        database.update("update tenant_membership set status = 'DISABLED' where identity_id = ? and tenant_id = ?",
            "oidc-user", TENANT_ID);

        mvc.perform(post("/api/accounts")
                .header("Authorization", "Bearer valid-token")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("An active company membership is required."));
    }

    private void stubValidToken() {
        var now = Instant.now();
        when(decoder.decode("valid-token")).thenReturn(Jwt.withTokenValue("valid-token")
            .header("alg", "RS256").subject("oidc-user").issuedAt(now).expiresAt(now.plusSeconds(300))
            .claim("roles", List.of("BUSINESS_DEVELOPMENT")).build());
    }
}
