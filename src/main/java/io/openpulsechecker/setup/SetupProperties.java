package io.openpulsechecker.setup;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.security.setup")
public record SetupProperties(
        long tokenTtlSeconds
) {
}
