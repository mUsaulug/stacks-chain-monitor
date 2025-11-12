package com.stacksmonitoring.api.exception;

import com.stacksmonitoring.infrastructure.logging.MdcContextHolder;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST API.
 * Handles validation errors, authentication failures, and business logic exceptions.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid request parameters")
                .details(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle authentication failures.
     */
    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationException(Exception ex) {
        log.error("Authentication failed: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Authentication Failed")
                .message("Invalid email or password")
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Handle illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("Business logic error: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle duplicate entity errors (UNIQUE constraint violations).
     * Example: Same block/transaction inserted twice (idempotent webhook delivery).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            WebRequest request
    ) {
        log.warn("Data integrity violation: {} (request_id={})",
                ex.getMostSpecificCause().getMessage(),
                MDC.get("request_id")
        );

        // Extract constraint name if available (for better error messages)
        String message = "Resource already exists or constraint violation";
        String errorCode = "DUPLICATE_RESOURCE";

        String causeMessage = ex.getMostSpecificCause().getMessage();
        if (causeMessage != null) {
            if (causeMessage.contains("uk_block_hash")) {
                message = "Block with this hash already exists";
                errorCode = "DUPLICATE_BLOCK";
            } else if (causeMessage.contains("uk_tx_id")) {
                message = "Transaction with this ID already exists";
                errorCode = "DUPLICATE_TRANSACTION";
            } else if (causeMessage.contains("uk_notification_rule_tx_event_channel")) {
                message = "Notification already exists for this rule/transaction/event combination";
                errorCode = "DUPLICATE_NOTIFICATION";
            }
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(message)
                .details(Map.of(
                        "error_code", errorCode,
                        "request_id", MDC.get("request_id") != null ? MDC.get("request_id") : "N/A",
                        "path", getPath(request)
                ))
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handle optimistic locking failures (concurrent modifications).
     * Example: Two users trying to update same alert rule simultaneously.
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            OptimisticLockException ex,
            WebRequest request
    ) {
        log.warn("Optimistic lock exception: {} (request_id={})",
                ex.getMessage(),
                MDC.get("request_id")
        );

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Concurrent Modification")
                .message("Resource was modified by another request. Please refresh and try again.")
                .details(Map.of(
                        "error_code", "OPTIMISTIC_LOCK_FAILURE",
                        "request_id", MDC.get("request_id") != null ? MDC.get("request_id") : "N/A",
                        "path", getPath(request),
                        "hint", "Refresh the resource and retry your request"
                ))
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handle rate limit exceeded errors.
     * Returns 429 with Retry-After header.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex,
            WebRequest request
    ) {
        log.warn("Rate limit exceeded: {} (request_id={})",
                ex.getMessage(),
                MDC.get("request_id")
        );

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message("Rate limit exceeded. Please try again later.")
                .details(Map.of(
                        "error_code", "RATE_LIMIT_EXCEEDED",
                        "request_id", MDC.get("request_id") != null ? MDC.get("request_id") : "N/A",
                        "path", getPath(request),
                        "retry_after_seconds", String.valueOf(ex.getRetryAfterSeconds())
                ))
                .build();

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(errorResponse);
    }

    /**
     * Handle generic exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred (request_id={})", MDC.get("request_id"), ex);

        // In production, hide stack traces (security)
        String message = "An unexpected error occurred";
        if (ex.getMessage() != null && !ex.getMessage().contains("Exception")) {
            message = ex.getMessage();
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message(message)
                .details(Map.of(
                        "error_code", "INTERNAL_ERROR",
                        "request_id", MDC.get("request_id") != null ? MDC.get("request_id") : "N/A",
                        "path", getPath(request)
                ))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Extract request path from WebRequest.
     */
    private String getPath(WebRequest request) {
        if (request instanceof ServletWebRequest) {
            return ((ServletWebRequest) request).getRequest().getRequestURI();
        }
        return "unknown";
    }
}
