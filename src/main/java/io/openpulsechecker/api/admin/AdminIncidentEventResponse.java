package io.openpulsechecker.api.admin;

import io.openpulsechecker.domain.IncidentState;
import io.openpulsechecker.incident.IncidentManualAction;
import java.time.Instant;
import java.util.UUID;

public record AdminIncidentEventResponse(
        UUID id,
        IncidentManualAction action,
        String actor,
        String reason,
        IncidentState fromState,
        IncidentState toState,
        Instant occurredAt
) {}
