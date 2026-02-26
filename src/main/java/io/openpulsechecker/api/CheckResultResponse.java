package io.openpulsechecker.api;

import io.openpulsechecker.domain.CheckStatus;
import java.time.Instant;
import java.util.UUID;

public record CheckResultResponse(
        UUID id,
        UUID monitorId,
        CheckStatus status,
        Integer statusCode,
        Long latencyMs,
        Instant checkedAt,
        String error
) {
}
