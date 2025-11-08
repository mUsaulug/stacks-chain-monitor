package com.stacksmonitoring.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for JWT-based authentication.
 * Configures security filter chain, authentication providers, and password encoding.
 *
 * Filter Execution Order (CRITICAL for security):
 * 1. ChainhookHmacFilter - Validates webhook HMAC signatures
 * 2. JwtAuthenticationFilter - Extracts JWT, populates SecurityContext
 * 3. RateLimitFilter - Enforces per-user rate limits (needs SecurityContext)
 *
 * Reference: CLAUDE.md P0-3 (Security Filter Ordering)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ChainhookHmacFilter chainhookHmacFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Configure security filter chain.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints (authentication not required)
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/webhook/**",
                                "/actuator/health",
                                "/actuator/info",
                                "/swagger-ui/**",
                                "/api-docs/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // Actuator endpoints require ADMIN role
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider())
                // CRITICAL: Filter order matters for security!
                // 1. HMAC validation (webhook signature check)
                .addFilterBefore(chainhookHmacFilter, UsernamePasswordAuthenticationFilter.class)
                // 2. JWT authentication (populate SecurityContext with user identity)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // 3. Rate limiting (AFTER authentication - needs SecurityContext for per-user limits)
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configure authentication provider with UserDetailsService and PasswordEncoder.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * BCrypt password encoder with strength 12.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Authentication manager bean.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
