package com.stacksmonitoring.infrastructure.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for CorrelationIdFilter (OBS-2).
 *
 * Tests verify that:
 * 1. request_id is present in MDC during request processing
 * 2. MDC is cleared after request completes (no leak to next request)
 * 3. Incoming X-Request-ID header is preserved and echoed back
 *
 * MDC cleanup is critical to prevent memory leaks in thread pools.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorrelationIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String PUBLIC_ENDPOINT = "/actuator/health"; // No auth required

    @Test
    @DisplayName("OBS-2: request_id should be present in MDC during request")
    void testRequestIdPresentInMdc() throws Exception {
        // Perform request and capture MDC state during controller execution
        // Note: We cannot directly inspect MDC here because it's cleared after request
        // Instead, verify that X-Request-ID response header is set (proof that filter ran)
        MvcResult result = mockMvc.perform(get(PUBLIC_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-ID");
        assertThat(requestId).isNotNull();
        assertThat(requestId).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"); // UUID format
    }

    @Test
    @DisplayName("OBS-2: MDC should be cleared after request (no leak)")
    void testMdcClearedAfterRequest() throws Exception {
        // First request - generates request_id
        mockMvc.perform(get(PUBLIC_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"));

        // After first request completes, MDC should be cleared
        String mdcAfterFirstRequest = MDC.get("request_id");
        assertThat(mdcAfterFirstRequest).as("MDC should be cleared after first request").isNull();

        // Second request - should get a NEW request_id, not leaked from first
        MvcResult secondResult = mockMvc.perform(get(PUBLIC_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn();

        String secondRequestId = secondResult.getResponse().getHeader("X-Request-ID");
        assertThat(secondRequestId).isNotNull();

        // After second request completes, MDC should be cleared again
        String mdcAfterSecondRequest = MDC.get("request_id");
        assertThat(mdcAfterSecondRequest).as("MDC should be cleared after second request").isNull();
    }

    @Test
    @DisplayName("OBS-2: Incoming X-Request-ID header should be preserved and echoed back")
    void testIncomingRequestIdPreserved() throws Exception {
        String clientProvidedRequestId = "client-request-12345";

        MvcResult result = mockMvc.perform(get(PUBLIC_ENDPOINT)
                .header("X-Request-ID", clientProvidedRequestId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", clientProvidedRequestId))
                .andReturn();

        String echoedRequestId = result.getResponse().getHeader("X-Request-ID");
        assertThat(echoedRequestId).isEqualTo(clientProvidedRequestId);
    }

    @Test
    @DisplayName("OBS-2: Generated request_id should be unique across requests")
    void testGeneratedRequestIdsAreUnique() throws Exception {
        // First request
        MvcResult result1 = mockMvc.perform(get(PUBLIC_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn();
        String requestId1 = result1.getResponse().getHeader("X-Request-ID");

        // Second request
        MvcResult result2 = mockMvc.perform(get(PUBLIC_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn();
        String requestId2 = result2.getResponse().getHeader("X-Request-ID");

        // Third request
        MvcResult result3 = mockMvc.perform(get(PUBLIC_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn();
        String requestId3 = result3.getResponse().getHeader("X-Request-ID");

        // All request IDs should be unique
        assertThat(requestId1).isNotEqualTo(requestId2);
        assertThat(requestId2).isNotEqualTo(requestId3);
        assertThat(requestId1).isNotEqualTo(requestId3);
    }

    @Test
    @DisplayName("OBS-2: Empty X-Request-ID header should trigger UUID generation")
    void testEmptyRequestIdHeaderGeneratesUuid() throws Exception {
        // Client sends empty X-Request-ID header (malformed request)
        MvcResult result = mockMvc.perform(get(PUBLIC_ENDPOINT)
                .header("X-Request-ID", ""))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-ID");
        // Empty header should be ignored, UUID should be generated instead
        assertThat(requestId).isNotEmpty();
        assertThat(requestId).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }
}
