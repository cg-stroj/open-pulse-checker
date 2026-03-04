package io.openpulsechecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.alerting.slack")
public record SlackAlertingProperties(
        boolean enabled,
        String webhookUrl,
        int maxAttempts,
        long initialBackoffMs
) {
}
