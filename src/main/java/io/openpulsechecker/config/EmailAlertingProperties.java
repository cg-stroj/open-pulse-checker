package io.openpulsechecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.alerting.email")
public record EmailAlertingProperties(
        boolean enabled,
        String url,
        String to,
        String bearerToken,
        int maxAttempts,
        long initialBackoffMs
) {
}
