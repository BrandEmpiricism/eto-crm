package com.brandempiricism.etocrm.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

@Configuration
@EnableMethodSecurity
@Profile({"local", "test"})
@ConditionalOnProperty(name = "eto.security.mode", havingValue = "actor-header")
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("The actor-header development identity bridge cannot run in production.");
        }
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/**").authenticated()
                        .requestMatchers("/api/**").permitAll().anyRequest().permitAll())
                .addFilterBefore(new ActorHeaderFilter(), BasicAuthenticationFilter.class)
                .build();
    }

    static final class ActorHeaderFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String actor = request.getHeader("X-Actor");
            if (actor != null && !actor.isBlank()) {
                MDC.put("actorId", actor.trim());
                var role = "platform-operator".equals(actor.trim())
                        ? SecurityRole.PLATFORM_OPERATOR : SecurityRole.BUSINESS_DEVELOPMENT;
                var authorities = role.permissions().stream().map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(actor.trim(), null, authorities));
            }
            try { chain.doFilter(request, response); } finally { MDC.remove("actorId"); }
        }
    }
}
