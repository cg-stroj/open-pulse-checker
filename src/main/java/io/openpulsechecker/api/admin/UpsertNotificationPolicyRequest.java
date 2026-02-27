package io.openpulsechecker.api.admin;

import io.openpulsechecker.notificationpolicy.NotificationPolicyScopeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record UpsertNotificationPolicyRequest(
        @NotNull NotificationPolicyScopeType scopeType,
        UUID scopeRefId,
        @NotNull Boolean enabled,
        @Min(0) int cooldownSeconds,
        @Min(0) int dedupSeconds,
        @Valid @NotEmpty List<NotificationPolicyRouteRequest> routes,
        @Valid List<NotificationEscalationStepRequest> escalationSteps
) {
}
