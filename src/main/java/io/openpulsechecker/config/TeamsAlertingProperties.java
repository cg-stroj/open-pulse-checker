package io.openpulsechecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.alerting.teams")
public record TeamsAlertingProperties(
        boolean enabled,
        String webhookUrl,
        int maxAttempts,
        long initialBackoffMs
) {
}
