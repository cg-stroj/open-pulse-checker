package io.openpulsechecker.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpsertStatusPageV2ConfigRequest(
        @Valid List<ComponentGroupItem> componentGroups,
        @Valid List<MonitorBindingItem> monitorBindings,
        @Valid List<MaintenanceAnnouncementItem> maintenanceAnnouncements
) {
    public record ComponentGroupItem(
            UUID id,
            @NotBlank @Size(max = 120) String name,
            @NotNull Integer displayOrder
    ) {}

    public record MonitorBindingItem(
            @NotNull UUID monitorId,
            @NotNull Integer displayOrder,
            UUID componentGroupId
    ) {}

    public record MaintenanceAnnouncementItem(
            UUID id,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String message,
            @NotNull Instant publishAt,
            Instant startsAt,
            Instant endsAt,
            @NotNull Boolean isPublic
    ) {}
}
