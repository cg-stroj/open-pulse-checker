package io.openpulsechecker.api;

import io.openpulsechecker.domain.MonitorType;
import java.time.Instant;
import java.util.UUID;

public record MonitorResponse(
        UUID id,
        String name,
        MonitorType type,
        String targetUrl,
        int intervalSec,
        boolean enabled,
        int timeoutMs,
        Instant createdAt,
        Instant updatedAt
) {
}
