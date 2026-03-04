package io.openpulsechecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.alerting.discord")
public record DiscordAlertingProperties(
        boolean enabled,
        String webhookUrl,
        int maxAttempts,
        long initialBackoffMs
) {
}
