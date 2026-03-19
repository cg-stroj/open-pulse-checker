package io.openpulsechecker.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.security")
public record SecurityProperties(
        String authMode,
        List<String> corsAllowedOrigins,
        boolean corsAllowCredentials
) {
}
