package com.brandempiricism.etocrm.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole("BUSINESS_DEVELOPMENT")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("BUSINESS_DEVELOPMENT")
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasRole("BUSINESS_DEVELOPMENT")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("BUSINESS_DEVELOPMENT")
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
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(actor.trim(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_DEVELOPMENT"))));
            }
            try { chain.doFilter(request, response); } finally { MDC.remove("actorId"); }
        }
    }
}
