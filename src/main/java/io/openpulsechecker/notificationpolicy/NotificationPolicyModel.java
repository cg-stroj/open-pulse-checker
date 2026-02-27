package io.openpulsechecker.notificationpolicy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    public record RouteRule(NotificationSeverity severity, boolean webhookEnabled) {}
    public record EscalationStep(int stepOrder, int afterSeconds, NotificationSeverity minSeverity, boolean webhookEnabled) {}
}
