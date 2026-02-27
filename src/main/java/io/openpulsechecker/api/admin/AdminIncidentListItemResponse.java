package io.openpulsechecker.api.admin;

import io.openpulsechecker.domain.IncidentState;
import java.time.Instant;
import java.util.UUID;

public record AdminIncidentListItemResponse(
        UUID id,
        UUID monitorId,
        String monitorName,
        IncidentState state,
        Instant openedAt,
        Instant resolvedAt,
        String reason
) {}
