package io.openpulsechecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.alerting.webhook")
public record AlertingProperties(
        boolean enabled,
        String url,
        int maxAttempts,
        long initialBackoffMs
) {
}
