package com.stacksmonitoring.api.controller;

import com.stacksmonitoring.api.dto.alert.AlertRuleResponse;
import com.stacksmonitoring.api.dto.alert.CreateAlertRuleRequest;
import com.stacksmonitoring.application.service.AlertRuleService;
import com.stacksmonitoring.domain.model.monitoring.AlertRule;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for managing alert rules.
 * Requires authentication via JWT.
 */
@RestController
@RequestMapping("/api/v1/alerts/rules")
@RequiredArgsConstructor
@Slf4j
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    /**
     * Create a new alert rule.
     */
    @PostMapping
    public ResponseEntity<AlertRuleResponse> createRule(
            @Valid @RequestBody CreateAlertRuleRequest request,
            Authentication authentication) {

        log.info("Creating alert rule for user {}", authentication.getName());

        AlertRule rule = alertRuleService.createRule(request, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AlertRuleResponse.fromEntity(rule));
    }

    /**
     * Get all rules for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<List<AlertRuleResponse>> getUserRules(Authentication authentication) {
        log.debug("Fetching rules for user {}", authentication.getName());

        List<AlertRule> rules = alertRuleService.getUserRules(authentication.getName());

        List<AlertRuleResponse> response = rules.stream()
                .map(AlertRuleResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get active rules for the authenticated user.
     */
    @GetMapping("/active")
    public ResponseEntity<List<AlertRuleResponse>> getActiveUserRules(Authentication authentication) {
        log.debug("Fetching active rules for user {}", authentication.getName());

        List<AlertRule> rules = alertRuleService.getActiveUserRules(authentication.getName());

        List<AlertRuleResponse> response = rules.stream()
                .map(AlertRuleResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific rule by ID.
     */
    @GetMapping("/{ruleId}")
    public ResponseEntity<AlertRuleResponse> getRule(@PathVariable Long ruleId) {
        log.debug("Fetching rule {}", ruleId);

        AlertRule rule = alertRuleService.getRule(ruleId);

        return ResponseEntity.ok(AlertRuleResponse.fromEntity(rule));
    }

    /**
     * Activate a rule.
     */
    @PutMapping("/{ruleId}/activate")
    public ResponseEntity<AlertRuleResponse> activateRule(@PathVariable Long ruleId) {
        log.info("Activating rule {}", ruleId);

        AlertRule rule = alertRuleService.updateRuleStatus(ruleId, true);

        return ResponseEntity.ok(AlertRuleResponse.fromEntity(rule));
    }

    /**
     * Deactivate a rule.
     */
    @PutMapping("/{ruleId}/deactivate")
    public ResponseEntity<AlertRuleResponse> deactivateRule(@PathVariable Long ruleId) {
        log.info("Deactivating rule {}", ruleId);

        AlertRule rule = alertRuleService.updateRuleStatus(ruleId, false);

        return ResponseEntity.ok(AlertRuleResponse.fromEntity(rule));
    }

    /**
     * Delete a rule.
     */
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long ruleId) {
        log.info("Deleting rule {}", ruleId);

        alertRuleService.deleteRule(ruleId);

        return ResponseEntity.noContent().build();
    }
}
