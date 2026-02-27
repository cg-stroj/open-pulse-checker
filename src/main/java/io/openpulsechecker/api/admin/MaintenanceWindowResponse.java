package io.openpulsechecker.api.admin;

import io.openpulsechecker.maintenance.MaintenancePolicy;
import io.openpulsechecker.maintenance.MaintenanceWindowScopeType;
import io.openpulsechecker.maintenance.MaintenanceWindowType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MaintenanceWindowResponse(
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
