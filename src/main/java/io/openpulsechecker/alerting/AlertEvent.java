package io.openpulsechecker.alerting;

import java.time.Instant;
import java.util.UUID;

public record AlertEvent(
        AlertEventType type,
        UUID monitorId,
        UUID incidentId,
        Instant occurredAt,
        String reason
) {
}
