package com.brandempiricism.etocrm.identity;

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
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name = "eto.security.mode", havingValue = "oidc")
class OidcSecurityConfiguration {
    @Bean
    SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http, ObjectMapper json) throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new LinkedHashSet<GrantedAuthority>();
            Collection<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) roles.stream().map(OidcSecurityConfiguration::knownRole)
                .flatMap(Optional::stream).flatMap(role -> role.permissions().stream())
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
                .requestMatchers("/api/**").permitAll().anyRequest().permitAll())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint((request, response, failure) -> unauthorized(response, json)))
            .addFilterAfter(new AuthenticatedActorCorrelationFilter(), BearerTokenAuthenticationFilter.class)
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
            if (authentication != null && authentication.isAuthenticated()) MDC.put("actorId", authentication.getName());
            try { chain.doFilter(request, response); } finally { MDC.remove("actorId"); }
        }
    }
}
