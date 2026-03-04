package io.openpulsechecker.api;

import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.domain.IncidentState;
import io.openpulsechecker.domain.StatusPageOverallStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicStatusPageResponse(
        StatusPageResponse page,
        StatusPageOverallStatus overallStatus,
        List<PublicComponentGroup> componentGroups,
        List<PublicMaintenanceAnnouncement> maintenanceAnnouncements,
        List<PublicMonitorSummary> monitors,
        List<IncidentTimelineItem> incidents
) {
    public record PublicComponentGroup(
            UUID id,
            String name,
            int displayOrder
    ) {}

    public record PublicMaintenanceAnnouncement(
            UUID id,
            String title,
            String message,
            Instant publishAt,
            Instant startsAt,
            Instant endsAt
    ) {}

    public record PublicMonitorSummary(
            UUID monitorId,
            String monitorName,
            int displayOrder,
            UUID componentGroupId,
            CheckStatus currentStatus,
            Integer statusCode,
            Long latencyMs,
            Instant checkedAt
    ) {}

    public record IncidentTimelineItem(
            UUID incidentId,
            UUID monitorId,
            String monitorName,
            IncidentState state,
            Instant openedAt,
            Instant resolvedAt,
            String reason
    ) {}
}
