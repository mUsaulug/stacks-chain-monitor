package com.stacksmonitoring.infrastructure.logging;

import org.slf4j.MDC;

/**
 * Utility class for managing MDC (Mapped Diagnostic Context) entries.
 *
 * <p>Provides convenient methods to add business context to logs:</p>
 * <ul>
 *   <li>user_id: Currently authenticated user</li>
 *   <li>block_hash: Blockchain block being processed</li>
 *   <li>tx_id: Transaction being processed</li>
 *   <li>notification_id: Notification being sent</li>
 *   <li>alert_rule_id: Alert rule being evaluated</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>
 * // In service class:
 * MdcContextHolder.setUserId(userId);
 * MdcContextHolder.setBlockHash(blockHash);
 * log.info("Processing block");
 * // Output: [request_id=abc-123] [user_id=456] [block_hash=0x789...] Processing block
 *
 * // Always clear when done:
 * MdcContextHolder.clearBlockHash();
 * </pre>
 *
 * <h3>JSON Logging Output:</h3>
 * <pre>
 * {
 *   "timestamp": "2025-11-11T10:30:45.123Z",
 *   "level": "INFO",
 *   "logger": "ProcessChainhookPayloadUseCase",
 *   "message": "Processing block",
 *   "request_id": "abc-123",
 *   "user_id": "456",
 *   "block_hash": "0x789..."
 * }
 * </pre>
 *
 * @see MDC
 * @see CorrelationIdFilter
 */
public final class MdcContextHolder {

    // MDC keys
    public static final String USER_ID = "user_id";
    public static final String BLOCK_HASH = "block_hash";
    public static final String TX_ID = "tx_id";
    public static final String NOTIFICATION_ID = "notification_id";
    public static final String ALERT_RULE_ID = "alert_rule_id";
    public static final String WEBHOOK_REQUEST_ID = "webhook_request_id";

    private MdcContextHolder() {
        // Utility class - no instantiation
    }

    // ========================================
    // SETTERS
    // ========================================

    /**
     * Set authenticated user ID in MDC.
     * Useful for tracking user actions across logs.
     *
     * @param userId user ID (can be null)
     */
    public static void setUserId(Long userId) {
        if (userId != null) {
            MDC.put(USER_ID, String.valueOf(userId));
        }
    }

    /**
     * Set blockchain block hash in MDC.
     * Useful for tracking block processing across logs.
     *
     * @param blockHash block hash (can be null)
     */
    public static void setBlockHash(String blockHash) {
        if (blockHash != null && !blockHash.isEmpty()) {
            MDC.put(BLOCK_HASH, blockHash);
        }
    }

    /**
     * Set transaction ID in MDC.
     * Useful for tracking transaction processing across logs.
     *
     * @param txId transaction ID (can be null)
     */
    public static void setTxId(String txId) {
        if (txId != null && !txId.isEmpty()) {
            MDC.put(TX_ID, txId);
        }
    }

    /**
     * Set notification ID in MDC.
     * Useful for tracking notification delivery across logs.
     *
     * @param notificationId notification ID (can be null)
     */
    public static void setNotificationId(Long notificationId) {
        if (notificationId != null) {
            MDC.put(NOTIFICATION_ID, String.valueOf(notificationId));
        }
    }

    /**
     * Set alert rule ID in MDC.
     * Useful for tracking rule evaluation across logs.
     *
     * @param alertRuleId alert rule ID (can be null)
     */
    public static void setAlertRuleId(Long alertRuleId) {
        if (alertRuleId != null) {
            MDC.put(ALERT_RULE_ID, String.valueOf(alertRuleId));
        }
    }

    /**
     * Set webhook request ID in MDC.
     * Links to raw_webhook_events table for event sourcing.
     *
     * @param webhookRequestId webhook request ID (can be null)
     */
    public static void setWebhookRequestId(String webhookRequestId) {
        if (webhookRequestId != null && !webhookRequestId.isEmpty()) {
            MDC.put(WEBHOOK_REQUEST_ID, webhookRequestId);
        }
    }

    // ========================================
    // GETTERS (for reading current context)
    // ========================================

    public static String getUserId() {
        return MDC.get(USER_ID);
    }

    public static String getBlockHash() {
        return MDC.get(BLOCK_HASH);
    }

    public static String getTxId() {
        return MDC.get(TX_ID);
    }

    public static String getNotificationId() {
        return MDC.get(NOTIFICATION_ID);
    }

    public static String getAlertRuleId() {
        return MDC.get(ALERT_RULE_ID);
    }

    public static String getWebhookRequestId() {
        return MDC.get(WEBHOOK_REQUEST_ID);
    }

    // ========================================
    // CLEARERS (prevent memory leaks)
    // ========================================

    public static void clearUserId() {
        MDC.remove(USER_ID);
    }

    public static void clearBlockHash() {
        MDC.remove(BLOCK_HASH);
    }

    public static void clearTxId() {
        MDC.remove(TX_ID);
    }

    public static void clearNotificationId() {
        MDC.remove(NOTIFICATION_ID);
    }

    public static void clearAlertRuleId() {
        MDC.remove(ALERT_RULE_ID);
    }

    public static void clearWebhookRequestId() {
        MDC.remove(WEBHOOK_REQUEST_ID);
    }

    /**
     * Clear all business context (keeps request_id from CorrelationIdFilter).
     * Use at the end of request processing.
     */
    public static void clearBusinessContext() {
        clearUserId();
        clearBlockHash();
        clearTxId();
        clearNotificationId();
        clearAlertRuleId();
        clearWebhookRequestId();
    }
}
