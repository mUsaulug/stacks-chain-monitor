package com.stacksmonitoring.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that generates and assigns a unique request ID to each HTTP request.
 * The request ID is stored in MDC (Mapped Diagnostic Context) for logging.
 *
 * OBS-2: Implements correlation ID tracking for request tracing.
 *
 * Features:
 * - Generates UUID for each request
 * - Stores in MDC with key "request_id"
 * - Adds X-Request-ID response header for client-side correlation
 * - Clears MDC after request to prevent memory leaks
 *
 * Usage in logs:
 * - logback pattern: %X{request_id}
 * - JSON logging: automatically included via LogstashEncoder
 *
 * Filter Order: HIGHEST_PRECEDENCE (runs before all other filters)
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_MDC_KEY = "request_id";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Generate or extract request ID
        String requestId = extractOrGenerateRequestId(request);

        // Store in MDC for logging
        MDC.put(REQUEST_ID_MDC_KEY, requestId);

        // Add to response header for client-side correlation
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: Clear MDC to prevent memory leaks in thread pools
            MDC.clear();
        }
    }

    /**
     * Extract request ID from incoming header or generate new UUID.
     * Allows clients to pass their own correlation IDs.
     */
    private String extractOrGenerateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);

        if (requestId != null && !requestId.isEmpty()) {
            // Client provided request ID - use it
            log.debug("Using client-provided request ID: {}", requestId);
            return requestId;
        }

        // Generate new UUID for this request
        return UUID.randomUUID().toString();
    }
}
