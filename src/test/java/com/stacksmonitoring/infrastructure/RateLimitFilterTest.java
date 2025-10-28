package com.stacksmonitoring.infrastructure;

import com.stacksmonitoring.infrastructure.config.RateLimitFilter;
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

import static org.mockito.Mockito.*;

/**
 * Unit tests for Rate Limit Filter.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rateLimitFilter, "rateLimitEnabled", true);
        ReflectionTestUtils.setField(rateLimitFilter, "requestsPerMinute", 5);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
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
        // Given - consume all tokens
        for (int i = 0; i < 5; i++) {
            rateLimitFilter.doFilter(request, response, filterChain);
        }

        // Reset mocks
        reset(filterChain, response);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getWriter()).thenReturn(mock(java.io.PrintWriter.class));

        // When - make one more request (should be rejected)
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
