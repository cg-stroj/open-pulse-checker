package io.openpulsechecker.maintenance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MaintenanceWindowModel(
        UUID id,
        String name,
        MaintenanceWindowScopeType scopeType,
        UUID scopeRefId,
        MaintenanceWindowType type,
        MaintenancePolicy policy,
        boolean enabled,
        Instant startAt,
        Instant endAt,
        String timezone,
        List<String> recurringDays,
        String recurringStartTime,
        String recurringEndTime,
        Instant createdAt,
        Instant updatedAt
) {
}
