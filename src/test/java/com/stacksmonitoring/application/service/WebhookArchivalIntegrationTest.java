package com.stacksmonitoring.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksmonitoring.domain.model.webhook.RawWebhookEvent;
import com.stacksmonitoring.domain.repository.RawWebhookEventRepository;
import com.stacksmonitoring.domain.valueobject.WebhookProcessingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for webhook archival (Event Sourcing pattern).
 *
 * Tests verify:
 * 1. Webhooks archived BEFORE processing (PENDING status)
 * 2. Status updated to PROCESSED on success
 * 3. Status updated to FAILED on processing error
 * 4. Status updated to REJECTED on validation error
 * 5. Replay capability for FAILED webhooks
 * 6. REJECTED webhooks cannot be replayed
 *
 * Reference: PART A.2 - Raw Webhook Events Archive [P1]
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:14:///test",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
class WebhookArchivalIntegrationTest {

    @Autowired
    private WebhookArchivalService webhookArchivalService;

    @Autowired
    private RawWebhookEventRepository webhookEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Valid webhook → archived with PENDING status, then PROCESSED")
    @Transactional
    void testValidWebhook_ArchivedWithProcessedStatus() {
        // Given - Create mock HTTP request with valid webhook payload
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("User-Agent", "Chainhook/1.0");
        request.addHeader("X-Signature", "abc123def456");
        request.addHeader("X-Signature-Timestamp", "1234567890");

        String payloadJson = """
            {
              "apply": [
                {
                  "block_identifier": {
                    "hash": "0xtest123",
                    "index": 1000
                  },
                  "timestamp": 1234567890,
                  "transactions": []
                }
              ],
              "rollback": []
            }
            """;

        // When - Archive incoming webhook
        RawWebhookEvent archived = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);

        // Then - Webhook archived with PENDING status
        assertThat(archived.getId()).isNotNull();
        assertThat(archived.getProcessingStatus()).isEqualTo(WebhookProcessingStatus.PENDING);
        assertThat(archived.getReceivedAt()).isNotNull();
        assertThat(archived.getProcessedAt()).isNull();
        assertThat(archived.getPayloadJson()).isEqualTo(payloadJson);
        assertThat(archived.getSourceIp()).isEqualTo("192.168.1.100");
        assertThat(archived.getUserAgent()).isEqualTo("Chainhook/1.0");
        assertThat(archived.getHeadersJson()).containsEntry("X-Signature", "abc123def456");
        assertThat(archived.getRequestId()).isNotNull();

        // When - Mark as processed
        webhookArchivalService.markAsProcessed(archived.getId());

        // Then - Status updated to PROCESSED
        RawWebhookEvent processed = webhookEventRepository.findById(archived.getId()).orElseThrow();
        assertThat(processed.getProcessingStatus()).isEqualTo(WebhookProcessingStatus.PROCESSED);
        assertThat(processed.getProcessedAt()).isNotNull();
        assertThat(processed.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Invalid webhook → archived with REJECTED status")
    @Transactional
    void testInvalidWebhook_ArchivedWithRejectedStatus() {
        // Given - Create mock request with invalid signature
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Signature", "invalid_signature");

        String payloadJson = "{\"apply\": [], \"rollback\": []}";

        // When - Archive and mark as rejected
        RawWebhookEvent archived = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);
        webhookArchivalService.markAsRejected(archived.getId(), "Invalid HMAC signature");

        // Then - Webhook marked as REJECTED
        RawWebhookEvent rejected = webhookEventRepository.findById(archived.getId()).orElseThrow();
        assertThat(rejected.getProcessingStatus()).isEqualTo(WebhookProcessingStatus.REJECTED);
        assertThat(rejected.getProcessedAt()).isNotNull();
        assertThat(rejected.getErrorMessage()).isEqualTo("Invalid HMAC signature");
        assertThat(rejected.canBeReplayed()).isFalse(); // Cannot replay REJECTED webhooks
    }

    @Test
    @DisplayName("Failed processing → archived with FAILED status, can be replayed")
    @Transactional
    void testFailedProcessing_ArchivedWithFailedStatus() {
        // Given - Archive webhook
        MockHttpServletRequest request = new MockHttpServletRequest();
        String payloadJson = "{\"apply\": [], \"rollback\": []}";
        RawWebhookEvent archived = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);

        // When - Mark as failed with exception
        Exception processingError = new RuntimeException("Database connection timeout");
        webhookArchivalService.markAsFailed(archived.getId(), processingError);

        // Then - Webhook marked as FAILED
        RawWebhookEvent failed = webhookEventRepository.findById(archived.getId()).orElseThrow();
        assertThat(failed.getProcessingStatus()).isEqualTo(WebhookProcessingStatus.FAILED);
        assertThat(failed.getProcessedAt()).isNotNull();
        assertThat(failed.getErrorMessage()).isEqualTo("Database connection timeout");
        assertThat(failed.getErrorStackTrace()).contains("RuntimeException");
        assertThat(failed.canBeReplayed()).isTrue(); // FAILED webhooks CAN be replayed
    }

    @Test
    @DisplayName("Find replayable webhooks → only FAILED and PENDING returned")
    @Transactional
    void testFindReplayableWebhooks_OnlyFailedAndPending() {
        // Given - Create webhooks with different statuses
        MockHttpServletRequest request = new MockHttpServletRequest();
        String payloadJson = "{\"apply\": [], \"rollback\": []}";

        // PENDING webhook
        RawWebhookEvent pending = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);

        // PROCESSED webhook
        RawWebhookEvent processed = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);
        webhookArchivalService.markAsProcessed(processed.getId());

        // FAILED webhook
        RawWebhookEvent failed = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);
        webhookArchivalService.markAsFailed(failed.getId(), new Exception("Test failure"));

        // REJECTED webhook
        RawWebhookEvent rejected = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);
        webhookArchivalService.markAsRejected(rejected.getId(), "Invalid signature");

        // When - Find replayable webhooks
        List<RawWebhookEvent> replayable = webhookEventRepository.findReplayableWebhooks();

        // Then - Only PENDING and FAILED returned
        assertThat(replayable).hasSize(2);
        assertThat(replayable)
            .extracting(RawWebhookEvent::getProcessingStatus)
            .containsExactlyInAnyOrder(WebhookProcessingStatus.PENDING, WebhookProcessingStatus.FAILED);
    }

    @Test
    @DisplayName("Count webhooks by status → accurate counts")
    @Transactional
    void testCountByStatus_AccurateCounts() {
        // Given - Create 2 PENDING, 3 PROCESSED, 1 FAILED, 1 REJECTED
        MockHttpServletRequest request = new MockHttpServletRequest();
        String payloadJson = "{\"apply\": [], \"rollback\": []}";

        // 2 PENDING
        webhookArchivalService.archiveIncomingWebhook(request, payloadJson);
        webhookArchivalService.archiveIncomingWebhook(request, payloadJson);

        // 3 PROCESSED
        for (int i = 0; i < 3; i++) {
            RawWebhookEvent webhook = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);
            webhookArchivalService.markAsProcessed(webhook.getId());
        }

        // 1 FAILED
        RawWebhookEvent failed = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);
        webhookArchivalService.markAsFailed(failed.getId(), new Exception("Test"));

        // 1 REJECTED
        RawWebhookEvent rejected = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);
        webhookArchivalService.markAsRejected(rejected.getId(), "Test");

        // When - Count by status
        long pendingCount = webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.PENDING);
        long processedCount = webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.PROCESSED);
        long failedCount = webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.FAILED);
        long rejectedCount = webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.REJECTED);

        // Then - Counts are accurate
        assertThat(pendingCount).isGreaterThanOrEqualTo(2);
        assertThat(processedCount).isGreaterThanOrEqualTo(3);
        assertThat(failedCount).isGreaterThanOrEqualTo(1);
        assertThat(rejectedCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Webhook payload stored as JSON → can be deserialized for replay")
    @Transactional
    void testPayloadJson_CanBeDeserialized() throws Exception {
        // Given - Archive webhook with complex payload
        MockHttpServletRequest request = new MockHttpServletRequest();
        String payloadJson = """
            {
              "apply": [
                {
                  "block_identifier": {"hash": "0xabc", "index": 100},
                  "timestamp": 1234567890,
                  "transactions": [
                    {
                      "transaction_identifier": {"hash": "0xtx123"},
                      "metadata": {
                        "sender": "SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR",
                        "success": true,
                        "fee": "1000"
                      }
                    }
                  ]
                }
              ],
              "rollback": []
            }
            """;

        RawWebhookEvent archived = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);

        // When - Retrieve and deserialize payload
        RawWebhookEvent retrieved = webhookEventRepository.findById(archived.getId()).orElseThrow();
        String retrievedJson = retrieved.getPayloadJson();

        // Then - JSON can be parsed
        assertThat(retrievedJson).isNotBlank();
        Object parsed = objectMapper.readValue(retrievedJson, Object.class);
        assertThat(parsed).isNotNull();
    }

    @Test
    @DisplayName("X-Forwarded-For header → client IP extracted correctly")
    @Transactional
    void testClientIpExtraction_XForwardedFor() {
        // Given - Request with X-Forwarded-For header (proxied request)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");
        request.setRemoteAddr("127.0.0.1"); // Proxy IP

        String payloadJson = "{\"apply\": [], \"rollback\": []}";

        // When - Archive webhook
        RawWebhookEvent archived = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);

        // Then - Original client IP extracted (first in X-Forwarded-For)
        assertThat(archived.getSourceIp()).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("Request ID generated → unique for each webhook")
    @Transactional
    void testRequestId_UniquePerWebhook() {
        // Given - Archive 3 webhooks
        MockHttpServletRequest request = new MockHttpServletRequest();
        String payloadJson = "{\"apply\": [], \"rollback\": []}";

        RawWebhookEvent webhook1 = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);
        RawWebhookEvent webhook2 = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);
        RawWebhookEvent webhook3 = webhookArchivalService.archiveIncomingWebhook(request, payloadJson);

        // Then - Request IDs are unique
        assertThat(webhook1.getRequestId()).isNotNull();
        assertThat(webhook2.getRequestId()).isNotNull();
        assertThat(webhook3.getRequestId()).isNotNull();

        assertThat(webhook1.getRequestId()).isNotEqualTo(webhook2.getRequestId());
        assertThat(webhook2.getRequestId()).isNotEqualTo(webhook3.getRequestId());
        assertThat(webhook1.getRequestId()).isNotEqualTo(webhook3.getRequestId());
    }
}
