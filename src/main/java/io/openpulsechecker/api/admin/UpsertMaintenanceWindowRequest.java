package io.openpulsechecker.api.admin;

import io.openpulsechecker.maintenance.MaintenancePolicy;
import io.openpulsechecker.maintenance.MaintenanceWindowScopeType;
import io.openpulsechecker.maintenance.MaintenanceWindowType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpsertMaintenanceWindowRequest(
        @NotBlank String name,
        @NotNull MaintenanceWindowScopeType scopeType,
        UUID scopeRefId,
        @NotNull MaintenanceWindowType type,
        @NotNull MaintenancePolicy policy,
        @NotNull Boolean enabled,
        Instant startAt,
        Instant endAt,
        String timezone,
        List<String> recurringDays,
        String recurringStartTime,
        String recurringEndTime
) {
}
