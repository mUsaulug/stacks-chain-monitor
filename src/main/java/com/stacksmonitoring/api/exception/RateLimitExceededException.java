package com.stacksmonitoring.api.exception;

import lombok.Getter;

/**
 * Exception thrown when rate limit is exceeded.
 * Results in 429 Too Many Requests response with Retry-After header.
 *
 * @see com.stacksmonitoring.infrastructure.security.RateLimitFilter
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitExceededException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitExceededException(int retryAfterSeconds) {
        super("Rate limit exceeded. Please retry after " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
