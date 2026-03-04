package io.openpulsechecker.api.admin;

import io.openpulsechecker.notificationpolicy.NotificationChannel;
import io.openpulsechecker.notificationpolicy.NotificationPolicyScopeType;
import io.openpulsechecker.notificationpolicy.NotificationSeverity;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record NotificationPolicyResponse(
        UUID id,
        NotificationPolicyScopeType scopeType,
        UUID scopeRefId,
        boolean enabled,
        int cooldownSeconds,
        int dedupSeconds,
        List<RouteResponse> routes,
        List<EscalationStepResponse> escalationSteps,
        Instant createdAt,
        Instant updatedAt
) {
    public record RouteResponse(NotificationSeverity severity, Set<NotificationChannel> channels) {}
    public record EscalationStepResponse(int stepOrder, int afterSeconds, NotificationSeverity minSeverity, Set<NotificationChannel> channels) {}
}
