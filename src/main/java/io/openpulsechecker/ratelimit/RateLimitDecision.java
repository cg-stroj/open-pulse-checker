package io.openpulsechecker.ratelimit;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
}
