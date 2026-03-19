package io.openpulsechecker.setup;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.security.setup")
public record SetupProperties(
        long tokenTtlSeconds,
        boolean bootstrapProtectionEnabled,
        String bootstrapSecret,
        List<String> bootstrapAllowedCidrs
) {
}
