package io.openpulsechecker.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.security.bootstrap-admin")
public record AdminBootstrapProperties(
        boolean enabled,
        String username,
        String password
) {
}
