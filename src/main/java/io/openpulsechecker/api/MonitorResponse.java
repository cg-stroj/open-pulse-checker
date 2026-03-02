package io.openpulsechecker.api;

import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.domain.HttpMethod;
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
        HttpMethod httpMethod,
        String expectedResponseKeyword,
        Instant lastCheckAt,
        CheckStatus lastCheckStatus,
        Integer lastStatusCode,
        Long lastLatencyMs,
        Instant createdAt,
        Instant updatedAt
) {
}
