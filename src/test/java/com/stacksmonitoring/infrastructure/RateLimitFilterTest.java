package com.stacksmonitoring.infrastructure;

import com.stacksmonitoring.infrastructure.config.RateLimitFilter;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Rate Limit Filter (Redis-backed).
 * Note: These tests mock the Redis ProxyManager for unit testing.
 * For full integration testing with Redis, see RateLimitIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private ProxyManager<String> proxyManager;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Bucket bucket;

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rateLimitFilter, "rateLimitEnabled", true);
        ReflectionTestUtils.setField(rateLimitFilter, "requestsPerMinute", 5);
        ReflectionTestUtils.setField(rateLimitFilter, "proxyManager", proxyManager);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // Mock ProxyManager to return our mock bucket
        @SuppressWarnings("unchecked")
        ProxyManager.BucketBuilder<String> bucketBuilder = mock(ProxyManager.BucketBuilder.class);
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(anyString(), any(Supplier.class))).thenReturn(bucket);

        // Default: bucket has tokens available
        when(bucket.tryConsume(1)).thenReturn(true);
    }

    @Test
    void shouldAllowRequestWhenUnderLimit() throws Exception {
        // When
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void shouldRejectRequestWhenOverLimit() throws Exception {
        // Given - bucket has no tokens
        when(bucket.tryConsume(1)).thenReturn(false);
        when(response.getWriter()).thenReturn(mock(java.io.PrintWriter.class));

        // When - make request (should be rejected)
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then
        verify(response).setStatus(429); // Too Many Requests
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldBypassWhenRateLimitDisabled() throws Exception {
        // Given
        ReflectionTestUtils.setField(rateLimitFilter, "rateLimitEnabled", false);

        // When - make many requests
        for (int i = 0; i < 10; i++) {
            rateLimitFilter.doFilter(request, response, filterChain);
        }

        // Then - all requests should pass
        verify(filterChain, times(10)).doFilter(request, response);
    }
}
