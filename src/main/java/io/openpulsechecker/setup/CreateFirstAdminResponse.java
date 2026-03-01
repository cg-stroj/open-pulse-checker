package io.openpulsechecker.setup;

import java.time.Instant;

public record CreateFirstAdminResponse(
        String username,
        String role,
        Instant createdAt
) {
}
