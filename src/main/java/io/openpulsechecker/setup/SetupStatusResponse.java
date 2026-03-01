package io.openpulsechecker.setup;

import java.time.Instant;

public record SetupStatusResponse(
        boolean setupRequired,
        boolean setupLocked,
        String setupToken,
        Instant setupTokenExpiresAt
) {
}
