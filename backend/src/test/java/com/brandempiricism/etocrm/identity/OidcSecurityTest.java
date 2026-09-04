package com.brandempiricism.etocrm.identity;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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

@SpringBootTest(properties = "eto.security.mode=oidc")
@AutoConfigureMockMvc
class OidcSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;

    @Test
    void verifiedSubjectAndKnownRoleAuthorizeAWrite() throws Exception {
        var now = Instant.now();
        when(decoder.decode("valid-token")).thenReturn(Jwt.withTokenValue("valid-token")
            .header("alg", "RS256").subject("oidc-user").issuedAt(now).expiresAt(now.plusSeconds(300))
            .claim("roles", List.of("BUSINESS_DEVELOPMENT")).build());

        mvc.perform(post("/api/accounts")
                .header("Authorization", "Bearer valid-token")
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
}
