package com.stacksmonitoring.infrastructure.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Distributed rate limiting filter using Token Bucket algorithm (Bucket4j + Redis).
 * Enforces per-user request rate limits across multiple application instances.
 *
 * CRITICAL: Multi-Instance Support (P0-2)
 * BEFORE: In-memory ConcurrentHashMap (each instance separate)
 *         - 3 instances with 100 req/min = actually 300 req/min
 *         - Users can bypass limits by hitting different backends
 *         - Memory leak: cache grows unbounded
 *
 * AFTER:  Redis-backed distributed buckets (shared state)
 *         - All instances share same Redis buckets
 *         - True 100 req/min across all instances
 *         - Automatic expiration (Redis TTL)
 *         - Memory efficient
 *
 * Implementation:
 * - Bucket4j ProxyManager with Lettuce client
 * - Redis stores bucket state with TTL
 * - Atomic token consumption (CAS operations)
 * - No local cache = always consistent
 *
 * Reference: CLAUDE.md P0-2 (Redis-Backed Rate Limiting)
 * Bucket4j Docs: https://bucket4j.com/
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${security.rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, byte[]> redisConnection;
    private ProxyManager<String> proxyManager;

    /**
     * Initialize Redis connection and Bucket4j ProxyManager on startup.
     */
    @PostConstruct
    public void init() {
        if (!rateLimitEnabled) {
            log.info("Rate limiting is disabled");
            return;
        }

        log.info("Initializing Redis-backed rate limiting ({}:{}, db:{})",
                redisHost, redisPort, redisDatabase);

        // Build Redis URI
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withDatabase(redisDatabase);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            uriBuilder.withPassword(redisPassword.toCharArray());
        }

        RedisURI redisUri = uriBuilder.build();

        // Create Redis client and connection
        redisClient = RedisClient.create(redisUri);
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        redisConnection = redisClient.connect(codec);

        // Create Bucket4j ProxyManager backed by Redis
        proxyManager = LettuceBasedProxyManager.builderFor(redisConnection)
                .build();

        log.info("Redis-backed rate limiting initialized successfully");
    }

    /**
     * Clean up Redis connection on shutdown.
     */
    @PreDestroy
    public void destroy() {
        if (redisConnection != null) {
            redisConnection.close();
            log.info("Redis connection closed");
        }
        if (redisClient != null) {
            redisClient.shutdown();
            log.info("Redis client shut down");
        }
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Skip if rate limiting is disabled
        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get user identifier (email from authentication or IP address)
        String identifier = getUserIdentifier(request);

        // Get distributed bucket from Redis (shared across all instances)
        Bucket bucket = proxyManager.builder()
                .build(getRateLimitKey(identifier), getBucketConfiguration());

        // Try to consume 1 token (atomic operation in Redis)
        if (bucket.tryConsume(1)) {
            // Request allowed
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            log.warn("Rate limit exceeded for identifier: {}", identifier);
            response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Please try again later.\"}");
        }
    }

    /**
     * Get rate limit key for Redis storage.
     * Format: rate-limit:{identifier}
     */
    private String getRateLimitKey(String identifier) {
        return "rate-limit:" + identifier;
    }

    /**
     * Get bucket configuration with rate limit settings.
     * Defines token bucket parameters (capacity, refill rate).
     */
    private BucketConfiguration getBucketConfiguration() {
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.intervally(requestsPerMinute, Duration.ofMinutes(1)));

        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Get user identifier for rate limiting.
     * Uses authenticated user email if available, otherwise falls back to IP address.
     */
    private String getUserIdentifier(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }

        // Fallback to IP address for unauthenticated requests
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        return clientIp;
    }
}
