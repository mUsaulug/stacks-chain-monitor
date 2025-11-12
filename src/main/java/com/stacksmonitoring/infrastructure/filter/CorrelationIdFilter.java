package com.stacksmonitoring.infrastructure.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to add correlation ID to all requests for distributed tracing.
 *
 * <p>This filter runs FIRST (Order = HIGHEST_PRECEDENCE) to ensure all subsequent
 * filters and components can use the correlation ID.</p>
 *
 * <h3>Behavior:</h3>
 * <ul>
 *   <li>If client sends X-Request-ID header → Use it (for distributed tracing)</li>
 *   <li>If no header → Generate new UUID</li>
 *   <li>Add to MDC (Mapped Diagnostic Context) → All logs include it</li>
 *   <li>Return in response header → Client can correlate requests</li>
 *   <li>Cleanup MDC after request → Prevent thread pollution</li>
 * </ul>
 *
 * <h3>Usage in Logs:</h3>
 * <pre>
 * // With MDC:
 * log.info("Processing webhook");
 * // Output: [request_id=abc-123] Processing webhook
 *
 * // Filter logs by request_id:
 * grep "request_id=abc-123" application.log
 * </pre>
 *
 * <h3>Client Usage:</h3>
 * <pre>
 * // Send request with tracking:
 * curl -H "X-Request-ID: my-custom-id" http://localhost:8080/api/v1/webhook
 *
 * // Response includes same ID:
 * X-Request-ID: my-custom-id
 * </pre>
 *
 * @see MDC
 * @see <a href="https://www.slf4j.org/manual.html#mdc">SLF4J MDC Documentation</a>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CorrelationIdFilter implements Filter {

    /**
     * HTTP header name for correlation ID.
     * Standard: X-Request-ID (used by AWS, Nginx, etc.)
     */
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    /**
     * MDC key for correlation ID.
     * Used in log pattern: %X{request_id}
     */
    public static final String REQUEST_ID_MDC_KEY = "request_id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // 1. Extract or generate correlation ID
            String requestId = extractOrGenerateRequestId(httpRequest);

            // 2. Add to MDC (for logging)
            MDC.put(REQUEST_ID_MDC_KEY, requestId);

            // 3. Add to response header (for client)
            httpResponse.setHeader(REQUEST_ID_HEADER, requestId);

            // 4. Log request with correlation ID
            log.debug("Request started: {} {} (request_id={})",
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    requestId
            );

            // 5. Continue filter chain
            chain.doFilter(request, response);

        } finally {
            // 6. CRITICAL: Clear MDC to prevent memory leaks
            // (Thread pool reuses threads, MDC is thread-local)
            MDC.clear();
        }
    }

    /**
     * Extract correlation ID from request header, or generate new one.
     *
     * @param request HTTP request
     * @return correlation ID (existing or new UUID)
     */
    private String extractOrGenerateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);

        if (requestId == null || requestId.trim().isEmpty()) {
            // No header provided → Generate new UUID
            requestId = UUID.randomUUID().toString();
            log.trace("Generated new request ID: {}", requestId);
        } else {
            log.trace("Using provided request ID: {}", requestId);
        }

        return requestId;
    }
}
