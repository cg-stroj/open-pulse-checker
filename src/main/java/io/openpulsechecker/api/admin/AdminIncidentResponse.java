package io.openpulsechecker.api.admin;

import io.openpulsechecker.domain.IncidentState;
import java.time.Instant;
import java.util.UUID;

public record AdminIncidentResponse(
        UUID id,
        UUID monitorId,
        IncidentState state,
        Instant openedAt,
        Instant resolvedAt,
        String reason
) {}
