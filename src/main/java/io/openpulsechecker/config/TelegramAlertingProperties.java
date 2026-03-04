package io.openpulsechecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.alerting.telegram")
public record TelegramAlertingProperties(
        boolean enabled,
        String botToken,
        String chatId,
        String apiBaseUrl,
        int maxAttempts,
        long initialBackoffMs
) {
}
