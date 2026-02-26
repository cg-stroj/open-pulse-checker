package io.openpulsechecker.apikey;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.security.bootstrap-api-key")
public record ApiKeyBootstrapProperties(
        boolean enabled,
        String keyId,
        String role,
        String secret
) {
}
