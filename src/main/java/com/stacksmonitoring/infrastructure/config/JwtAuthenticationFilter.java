package com.stacksmonitoring.infrastructure.config;

import com.stacksmonitoring.application.service.TokenRevocationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter for validating JWT tokens in request headers.
 * Extracts and validates JWT token from Authorization header (Bearer scheme).
 *
 * Security Validations (OWASP JWT Cheat Sheet):
 * 1. Signature validation (RS256 public key)
 * 2. Expiration check
 * 3. Issuer validation
 * 4. Token revocation check (denylist)
 * 5. Fingerprint validation (cookie binding)
 *
 * Reference: CLAUDE.md P0-1 (JWT RS256 Migration)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenRevocationService tokenRevocationService;

    private static final String FINGERPRINT_COOKIE_NAME = "X-Fingerprint";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Skip if no Authorization header or not Bearer token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Extract JWT token
            final String jwt = authHeader.substring(7);
            final String userEmail = jwtTokenService.extractUsername(jwt);

            // CRITICAL: Check if token is revoked (logout, security breach, etc.)
            if (tokenRevocationService.isTokenRevoked(jwt)) {
                log.warn("Revoked token attempted for user: {}", userEmail);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
                return;
            }

            // CRITICAL: Validate fingerprint (sidejacking prevention)
            // P0-SEC-2: Fingerprint cookie is MANDATORY - reject if missing or invalid
            String fingerprintCookie = extractFingerprintCookie(request);
            if (fingerprintCookie == null || !jwtTokenService.validateFingerprint(jwt, fingerprintCookie)) {
                if (fingerprintCookie == null) {
                    log.warn("Missing fingerprint cookie for user: {} - potential token sidejacking", userEmail);
                } else {
                    log.warn("Fingerprint mismatch for user: {} - potential token sidejacking", userEmail);
                }
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing token fingerprint");
                return;
            }

            // Validate token and set authentication
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (jwtTokenService.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("JWT authentication successful for user: {}", userEmail);
                }
            }
        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract fingerprint value from HttpOnly cookie.
     * Used for token sidejacking prevention.
     */
    private String extractFingerprintCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (FINGERPRINT_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
