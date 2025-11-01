package com.stacksmonitoring.api.controller;

import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.model.user.User;
import com.stacksmonitoring.domain.repository.AlertNotificationRepository;
import com.stacksmonitoring.domain.repository.UserRepository;
import com.stacksmonitoring.domain.valueobject.NotificationStatus;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for viewing alert notifications.
 * Requires authentication via JWT.
 */
@RestController
@RequestMapping("/api/v1/alerts/notifications")
@RequiredArgsConstructor
@Slf4j
public class AlertNotificationController {

    private final AlertNotificationRepository alertNotificationRepository;
    private final UserRepository userRepository;

    /**
     * Get notifications for the authenticated user (paginated).
     */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getUserNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<AlertNotification> notifications = alertNotificationRepository.findByUserId(user.getId(), pageable);

        Page<NotificationResponse> response = notifications.map(NotificationResponse::fromEntity);

        return ResponseEntity.ok(response);
    }

    /**
     * Get notifications by status.
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<NotificationResponse>> getNotificationsByStatus(
            Authentication authentication,
            @PathVariable NotificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<AlertNotification> notifications =
                alertNotificationRepository.findByUserIdAndStatus(user.getId(), status, pageable);

        Page<NotificationResponse> response = notifications.map(NotificationResponse::fromEntity);

        return ResponseEntity.ok(response);
    }

    /**
     * Get notification statistics for the user.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getNotificationStats(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> stats = new HashMap<>();

        // Count notifications by status
        long totalNotifications = alertNotificationRepository.findByUserId(user.getId(), PageRequest.of(0, 1)).getTotalElements();
        long sentNotifications = alertNotificationRepository.findByUserIdAndStatus(user.getId(), NotificationStatus.SENT, PageRequest.of(0, 1)).getTotalElements();
        long pendingNotifications = alertNotificationRepository.findByUserIdAndStatus(user.getId(), NotificationStatus.PENDING, PageRequest.of(0, 1)).getTotalElements();
        long failedNotifications = alertNotificationRepository.findByUserIdAndStatus(user.getId(), NotificationStatus.FAILED, PageRequest.of(0, 1)).getTotalElements();

        stats.put("total", totalNotifications);
        stats.put("sent", sentNotifications);
        stats.put("pending", pendingNotifications);
        stats.put("failed", failedNotifications);

        return ResponseEntity.ok(stats);
    }

    /**
     * DTO for notification response.
     */
    @Data
    public static class NotificationResponse {
        private Long id;
        private Long alertRuleId;
        private String alertRuleName;
        private String channel;
        private String status;
        private Instant triggeredAt;
        private Instant sentAt;
        private String message;
        private String failureReason;
        private Integer attemptCount;
        private String transactionId;
        private Long blockHeight;

        public static NotificationResponse fromEntity(AlertNotification notification) {
            NotificationResponse response = new NotificationResponse();

            response.setId(notification.getId());
            response.setAlertRuleId(notification.getAlertRule().getId());
            response.setAlertRuleName(notification.getAlertRule().getRuleName());
            response.setChannel(notification.getChannel().toString());
            response.setStatus(notification.getStatus().toString());
            response.setTriggeredAt(notification.getTriggeredAt());
            response.setSentAt(notification.getSentAt());
            response.setMessage(notification.getMessage());
            response.setFailureReason(notification.getFailureReason());
            response.setAttemptCount(notification.getAttemptCount());

            if (notification.getTransaction() != null) {
                response.setTransactionId(notification.getTransaction().getTxId());
                response.setBlockHeight(notification.getTransaction().getBlock().getBlockHeight());
            }

            return response;
        }
    }
}
