package io.openpulsechecker.api;

import java.time.Instant;
import java.util.UUID;

public record StatusPageResponse(
        UUID id,
        String name,
        String slug,
        boolean isPublic,
        StatusPageBrandingResponse branding,
        Instant createdAt,
        Instant updatedAt
) {
}
