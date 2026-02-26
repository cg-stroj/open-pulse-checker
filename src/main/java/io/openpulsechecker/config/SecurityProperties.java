package io.openpulsechecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.security")
public record SecurityProperties(
        String adminUsername,
        String adminPassword,
        String viewerUsername,
        String viewerPassword
) {
}
