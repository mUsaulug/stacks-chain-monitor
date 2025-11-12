package com.stacksmonitoring.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.model.monitoring.ContractCallAlertRule;
import com.stacksmonitoring.domain.repository.AlertNotificationRepository;
import com.stacksmonitoring.domain.valueobject.AlertSeverity;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for notification services.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServicesTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private DeadLetterQueueService dlqService;

    @Mock
    private AlertNotificationRepository notificationRepository;

    private EmailNotificationService emailService;
    private WebhookNotificationService webhookService;

    @BeforeEach
    void setUp() {
        emailService = new EmailNotificationService(mailSender, dlqService, notificationRepository);
        ReflectionTestUtils.setField(emailService, "emailEnabled", true);
        ReflectionTestUtils.setField(emailService, "fromEmail", "test@example.com");

        ObjectMapper objectMapper = new ObjectMapper();
        webhookService = new WebhookNotificationService(restTemplate, objectMapper, dlqService, notificationRepository);
    }

    // EmailNotificationService Tests

    @Test
    void testEmailService_Send_ShouldSendEmail() throws Exception {
        // Given
        AlertNotification notification = createTestNotification(NotificationChannel.EMAIL);
        notification.getAlertRule().setNotificationEmails("recipient@example.com");

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // When
        emailService.send(notification);

        // Then
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.getTo()).contains("recipient@example.com");
        assertThat(sentMessage.getFrom()).isEqualTo("test@example.com");
        assertThat(sentMessage.getSubject()).contains(AlertSeverity.MEDIUM.toString());
    }

    @Test
    void testEmailService_Send_WithMultipleRecipients_ShouldSendToAll() throws Exception {
        // Given
        AlertNotification notification = createTestNotification(NotificationChannel.EMAIL);
        notification.getAlertRule().setNotificationEmails("user1@example.com,user2@example.com");

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // When
        emailService.send(notification);

        // Then
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.getTo()).hasSize(2);
    }

    @Test
    void testEmailService_Send_WithNoEmails_ShouldThrowException() {
        // Given
        AlertNotification notification = createTestNotification(NotificationChannel.EMAIL);
        notification.getAlertRule().setNotificationEmails(null);

        // When & Then
        assertThatThrownBy(() -> emailService.send(notification))
                .isInstanceOf(NotificationService.NotificationException.class)
                .hasMessageContaining("No email addresses configured");
    }

    @Test
    void testEmailService_Send_WhenDisabled_ShouldThrowException() {
        // Given
        ReflectionTestUtils.setField(emailService, "emailEnabled", false);
        AlertNotification notification = createTestNotification(NotificationChannel.EMAIL);
        notification.getAlertRule().setNotificationEmails("test@example.com");

        // When & Then
        assertThatThrownBy(() -> emailService.send(notification))
                .isInstanceOf(NotificationService.NotificationException.class)
                .hasMessageContaining("Email notifications are disabled");
    }

    @Test
    void testEmailService_Supports_ShouldReturnTrueForEmail() {
        // Given
        AlertNotification notification = createTestNotification(NotificationChannel.EMAIL);

        // When & Then
        assertThat(emailService.supports(notification)).isTrue();
    }

    @Test
    void testEmailService_Supports_ShouldReturnFalseForWebhook() {
        // Given
        AlertNotification notification = createTestNotification(NotificationChannel.WEBHOOK);

        // When & Then
        assertThat(emailService.supports(notification)).isFalse();
    }

    // WebhookNotificationService Tests

    @Test
    void testWebhookService_Send_ShouldPostToWebhook() throws Exception {
        // Given
        AlertNotification notification = createTestNotification(NotificationChannel.WEBHOOK);
        notification.getAlertRule().setWebhookUrl("https://example.com/webhook");

        ResponseEntity<String> mockResponse = new ResponseEntity<>("OK", HttpStatus.OK);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        // When
        webhookService.send(notification);

        // Then
        verify(restTemplate, times(1)).exchange(
                eq("https://example.com/webhook"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void testWebhookService_Send_WithNon2xxResponse_ShouldThrowException() {
        // Given
        AlertNotification notification = createTestNotification(NotificationChannel.WEBHOOK);
        notification.getAlertRule().setWebhookUrl("https://example.com/webhook");

        ResponseEntity<String> mockResponse = new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        // When & Then
        assertThatThrownBy(() -> webhookService.send(notification))
                .isInstanceOf(NotificationService.NotificationException.class)
                .hasMessageContaining("non-2xx status");
    }

    @Test
    void testWebhookService_Send_WithNoUrl_ShouldThrowException() {
        // Given
        AlertNotification notification = createTestNotification(NotificationChannel.WEBHOOK);
        notification.getAlertRule().setWebhookUrl(null);

        // When & Then
        assertThatThrownBy(() -> webhookService.send(notification))
                .isInstanceOf(NotificationService.NotificationException.class)
                .hasMessageContaining("No webhook URL configured");
    }

    @Test
    void testWebhookService_Send_WithRestClientException_ShouldThrowException() {
        // Given
        AlertNotification notification = createTestNotification(NotificationChannel.WEBHOOK);
        notification.getAlertRule().setWebhookUrl("https://example.com/webhook");

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RestClientException("Connection failed"));

        // When & Then
        assertThatThrownBy(() -> webhookService.send(notification))
                .isInstanceOf(NotificationService.NotificationException.class)
                .hasMessageContaining("Failed to send webhook");
    }

    @Test
    void testWebhookService_Supports_ShouldReturnTrueForWebhook() {
        // Given
        AlertNotification notification = createTestNotification(NotificationChannel.WEBHOOK);

        // When & Then
        assertThat(webhookService.supports(notification)).isTrue();
    }

    @Test
    void testWebhookService_Supports_ShouldReturnFalseForEmail() {
        // Given
        AlertNotification notification = createTestNotification(NotificationChannel.EMAIL);

        // When & Then
        assertThat(webhookService.supports(notification)).isFalse();
    }

    // Helper methods

    private AlertNotification createTestNotification(NotificationChannel channel) {
        AlertNotification notification = new AlertNotification();

        ContractCallAlertRule rule = new ContractCallAlertRule();
        rule.setId(1L);
        rule.setRuleName("Test Alert Rule");
        rule.setSeverity(AlertSeverity.MEDIUM);
        rule.setNotificationChannels(List.of(channel));

        notification.setAlertRule(rule);
        notification.setChannel(channel);
        notification.setTriggeredAt(Instant.now());
        notification.setMessage("Test alert message");

        StacksTransaction transaction = new StacksTransaction();
        transaction.setTxId("0xtx123");
        transaction.setSender("SP123");
        transaction.setSuccess(true);

        StacksBlock block = new StacksBlock();
        block.setBlockHeight(1000L);
        block.setBlockHash("0xblock123");
        transaction.setBlock(block);

        notification.setTransaction(transaction);

        return notification;
    }
}
