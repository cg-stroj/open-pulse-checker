package io.openpulsechecker.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StatusPageV2ConfigResponse(
        List<ComponentGroupItem> componentGroups,
        List<MonitorBindingItem> monitorBindings,
        List<MaintenanceAnnouncementItem> maintenanceAnnouncements
) {
    public record ComponentGroupItem(UUID id, String name, int displayOrder) {}

    public record MonitorBindingItem(UUID monitorId, int displayOrder, UUID componentGroupId) {}

    public record MaintenanceAnnouncementItem(
            UUID id,
            String title,
            String message,
            Instant publishAt,
            Instant startsAt,
            Instant endsAt,
            boolean isPublic
    ) {}
}
