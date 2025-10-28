package com.stacksmonitoring.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksmonitoring.api.dto.alert.CreateAlertRuleRequest;
import com.stacksmonitoring.application.service.AlertRuleService;
import com.stacksmonitoring.domain.model.monitoring.ContractCallAlertRule;
import com.stacksmonitoring.domain.model.user.User;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import com.stacksmonitoring.domain.valueobject.AlertSeverity;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AlertRuleController.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertRuleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlertRuleService alertRuleService;

    @Test
    @WithMockUser(username = "testuser")
    void testCreateRule_WithValidRequest_ShouldReturnCreated() throws Exception {
        // Given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setRuleName("Test Contract Call Alert");
        request.setDescription("Alert for specific contract calls");
        request.setRuleType(AlertRuleType.CONTRACT_CALL);
        request.setSeverity(AlertSeverity.MEDIUM);
        request.setCooldownMinutes(60);
        request.setNotificationChannels(List.of(NotificationChannel.EMAIL));
        request.setNotificationEmails("test@example.com");
        request.setContractIdentifier("SP123.my-contract");
        request.setFunctionName("transfer");

        ContractCallAlertRule mockRule = new ContractCallAlertRule();
        mockRule.setId(1L);
        mockRule.setRuleName(request.getRuleName());
        mockRule.setSeverity(request.getSeverity());

        when(alertRuleService.createRule(any(), anyString())).thenReturn(mockRule);

        // When & Then
        mockMvc.perform(post("/api/v1/alerts/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ruleName").value("Test Contract Call Alert"))
                .andExpect(jsonPath("$.severity").value("MEDIUM"));

        verify(alertRuleService).createRule(any(), anyString());
    }

    @Test
    @WithMockUser(username = "testuser")
    void testGetUserRules_ShouldReturnRulesList() throws Exception {
        // Given
        ContractCallAlertRule mockRule = new ContractCallAlertRule();
        mockRule.setId(1L);
        mockRule.setRuleName("Test Rule");
        mockRule.setSeverity(AlertSeverity.MEDIUM);

        when(alertRuleService.getUserRules(anyString())).thenReturn(List.of(mockRule));

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].ruleName").value("Test Rule"));

        verify(alertRuleService).getUserRules("testuser");
    }

    @Test
    @WithMockUser(username = "testuser")
    void testGetActiveUserRules_ShouldReturnActiveRules() throws Exception {
        // Given
        ContractCallAlertRule mockRule = new ContractCallAlertRule();
        mockRule.setId(1L);
        mockRule.setRuleName("Active Rule");
        mockRule.setIsActive(true);

        when(alertRuleService.getActiveUserRules(anyString())).thenReturn(List.of(mockRule));

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/rules/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(alertRuleService).getActiveUserRules("testuser");
    }

    @Test
    @WithMockUser(username = "testuser")
    void testGetRule_ShouldReturnSpecificRule() throws Exception {
        // Given
        Long ruleId = 1L;
        ContractCallAlertRule mockRule = new ContractCallAlertRule();
        mockRule.setId(ruleId);
        mockRule.setRuleName("Specific Rule");

        when(alertRuleService.getRule(ruleId)).thenReturn(mockRule);

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/rules/{ruleId}", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruleId))
                .andExpect(jsonPath("$.ruleName").value("Specific Rule"));

        verify(alertRuleService).getRule(ruleId);
    }

    @Test
    @WithMockUser(username = "testuser")
    void testActivateRule_ShouldReturnActivatedRule() throws Exception {
        // Given
        Long ruleId = 1L;
        ContractCallAlertRule mockRule = new ContractCallAlertRule();
        mockRule.setId(ruleId);
        mockRule.setIsActive(true);

        when(alertRuleService.updateRuleStatus(ruleId, true)).thenReturn(mockRule);

        // When & Then
        mockMvc.perform(put("/api/v1/alerts/rules/{ruleId}/activate", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruleId))
                .andExpect(jsonPath("$.isActive").value(true));

        verify(alertRuleService).updateRuleStatus(ruleId, true);
    }

    @Test
    @WithMockUser(username = "testuser")
    void testDeactivateRule_ShouldReturnDeactivatedRule() throws Exception {
        // Given
        Long ruleId = 1L;
        ContractCallAlertRule mockRule = new ContractCallAlertRule();
        mockRule.setId(ruleId);
        mockRule.setIsActive(false);

        when(alertRuleService.updateRuleStatus(ruleId, false)).thenReturn(mockRule);

        // When & Then
        mockMvc.perform(put("/api/v1/alerts/rules/{ruleId}/deactivate", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruleId))
                .andExpect(jsonPath("$.isActive").value(false));

        verify(alertRuleService).updateRuleStatus(ruleId, false);
    }

    @Test
    @WithMockUser(username = "testuser")
    void testDeleteRule_ShouldReturnNoContent() throws Exception {
        // Given
        Long ruleId = 1L;

        // When & Then
        mockMvc.perform(delete("/api/v1/alerts/rules/{ruleId}", ruleId))
                .andExpect(status().isNoContent());

        verify(alertRuleService).deleteRule(ruleId);
    }

    @Test
    void testCreateRule_WithoutAuthentication_ShouldReturn401() throws Exception {
        // Given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setRuleName("Test Rule");
        request.setRuleType(AlertRuleType.CONTRACT_CALL);
        request.setSeverity(AlertSeverity.MEDIUM);
        request.setNotificationChannels(List.of(NotificationChannel.EMAIL));

        // When & Then
        mockMvc.perform(post("/api/v1/alerts/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
