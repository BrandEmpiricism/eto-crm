package com.brandempiricism.etocrm.identity;

import com.brandempiricism.etocrm.commons.DiagnosticContext;
import com.brandempiricism.etocrm.commons.DiagnosticEvents;
import com.brandempiricism.etocrm.commons.DiagnosticEvents.Event;
import com.brandempiricism.etocrm.commons.DiagnosticEvents.Outcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name = "eto.security.mode", havingValue = "oidc")
class OidcSecurityConfiguration {
    @Bean
    SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http, ObjectMapper json,
                                                IdentityApplicationApi identities) throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new LinkedHashSet<GrantedAuthority>();
            Object roles = jwt.getClaims().get("roles");
            if (roles instanceof Collection<?> values) values.stream().filter(String.class::isInstance)
                .map(String.class::cast).map(OidcSecurityConfiguration::knownRole)
                .flatMap(Optional::stream).flatMap(role -> role.permissions().stream())
                .filter(Permissions.PLATFORM_OPERATE::equals)
                .map(SimpleGrantedAuthority::new).forEach(authorities::add);
            return authorities;
        });

        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/**").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/**").authenticated()
                .requestMatchers("/api/platform").permitAll()
                .requestMatchers("/api/**").authenticated().anyRequest().permitAll())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint((request, response, failure) -> unauthorized(response, json)))
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, failure) -> unauthorized(response, json))
                .accessDeniedHandler((request, response, failure) -> {
                    DiagnosticEvents.emit(Event.AUTHORIZATION_REJECTED, Outcome.rejected);
                    problem(response, json, 403, "Access denied", "The requested operation is not permitted.");
                }))
            .addFilterAfter(new AuthenticatedActorCorrelationFilter(), BearerTokenAuthenticationFilter.class)
            .addFilterAfter(new TenantMembershipFilter(identities, json), AuthenticatedActorCorrelationFilter.class)
            .build();
    }

    private static Optional<SecurityRole> knownRole(String claim) {
        try {
            return Optional.of(SecurityRole.valueOf(claim));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    private static void unauthorized(HttpServletResponse response, ObjectMapper json) throws IOException {
        DiagnosticEvents.emit(Event.AUTHENTICATION_REJECTED, Outcome.rejected);
        var detail = ProblemDetail.forStatus(401);
        detail.setTitle("Authentication failed");
        detail.setDetail("A valid bearer token is required.");
        detail.setType(URI.create("https://eto-crm.example/problems/authentication"));
        detail.setProperty("requestId", MDC.get("requestId"));
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), detail);
    }

    static final class AuthenticatedActorCorrelationFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                        HttpServletResponse response,
                                        jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, IOException {
            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken && authentication.isAuthenticated()) {
                DiagnosticContext.verifiedIdentity(authentication.getName(), null);
                DiagnosticEvents.emit(Event.AUTHENTICATED, Outcome.success);
            }
            chain.doFilter(request, response);
        }
    }

    static final class TenantMembershipFilter extends OncePerRequestFilter {
        private static final String TENANT_HEADER = "X-Tenant-Id";
        private final IdentityApplicationApi identities;
        private final ObjectMapper json;

        TenantMembershipFilter(IdentityApplicationApi identities, ObjectMapper json) {
            this.identities = identities;
            this.json = json;
        }

        @Override
        protected boolean shouldNotFilter(jakarta.servlet.http.HttpServletRequest request) {
            return !request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/api/platform/");
        }

        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                        HttpServletResponse response,
                                        jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, IOException {
            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) {
                chain.doFilter(request, response);
                return;
            }
            try {
                var tenantHeaders = java.util.Collections.list(request.getHeaders(TENANT_HEADER));
                if (tenantHeaders.size() != 1) throw new IllegalArgumentException("Exactly one tenant is required.");
                var tenantHeader = tenantHeaders.getFirst();
                if (tenantHeader == null || tenantHeader.isBlank()) throw new IllegalArgumentException("Tenant is required.");
                var tenantId = java.util.UUID.fromString(tenantHeader);
                var context = identities.selectTenant(authentication.getName(), tenantId);
                var role = SecurityRole.valueOf(context.role().name());
                var authorities = role.permissions().stream().map(SimpleGrantedAuthority::new).toList();
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new JwtAuthenticationToken(jwt.getToken(), authorities, authentication.getName()));
                TenantContextHolder.bind(context);
                DiagnosticContext.verifiedIdentity(authentication.getName(), tenantId);
                DiagnosticEvents.emit(Event.MEMBERSHIP_ACCEPTED, Outcome.success);
            } catch (IllegalArgumentException | TenantAccessDeniedException denied) {
                forbidden(response, json);
                return;
            } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException unavailable) {
                DiagnosticEvents.emit(Event.MEMBERSHIP_UNAVAILABLE, Outcome.failure, unavailable);
                problem(response, json, 503, "Service unavailable", "Identity verification is temporarily unavailable.");
                return;
            }
            try {
                chain.doFilter(request, response);
            } finally {
                TenantContextHolder.clear();
            }
        }
    }

    private static void forbidden(HttpServletResponse response, ObjectMapper json) throws IOException {
        DiagnosticEvents.emit(Event.MEMBERSHIP_REJECTED, Outcome.rejected);
        var detail = ProblemDetail.forStatus(403);
        detail.setTitle("Tenant access denied");
        detail.setDetail("An active company membership is required.");
        detail.setType(URI.create("https://eto-crm.example/problems/tenant-access-denied"));
        detail.setProperty("requestId", MDC.get("requestId"));
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), detail);
    }

    private static void problem(HttpServletResponse response, ObjectMapper json, int status, String title, String message) throws IOException {
        var detail = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(status), message);
        detail.setTitle(title);
        detail.setProperty("requestId", MDC.get("requestId"));
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), detail);
    }
}
