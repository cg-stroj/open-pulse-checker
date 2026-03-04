package io.openpulsechecker.notificationpolicy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.List;

public record NotificationPolicyModel(
        UUID id,
        NotificationPolicyScopeType scopeType,
        UUID scopeRefId,
        boolean enabled,
        int cooldownSeconds,
        int dedupSeconds,
        List<RouteRule> routes,
        List<EscalationStep> escalationSteps,
        Instant createdAt,
        Instant updatedAt
) {
    public record RouteRule(NotificationSeverity severity, Set<NotificationChannel> channels) {}
    public record EscalationStep(int stepOrder, int afterSeconds, NotificationSeverity minSeverity, Set<NotificationChannel> channels) {}
}
