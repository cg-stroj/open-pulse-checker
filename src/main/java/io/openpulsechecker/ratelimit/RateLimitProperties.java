package io.openpulsechecker.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.rate-limit.sensitive")
public record RateLimitProperties(
        int capacity,
        int refillTokens,
        int refillPeriodSeconds
) {
}
