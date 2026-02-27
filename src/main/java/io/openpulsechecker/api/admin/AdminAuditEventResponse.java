package io.openpulsechecker.api.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditEventResponse(
        UUID id,
        String actor,
        String action,
        String resource,
        String outcome,
        String details,
        Instant occurredAt
) {
}
