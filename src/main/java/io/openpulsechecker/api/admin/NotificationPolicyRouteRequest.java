package io.openpulsechecker.api.admin;

import io.openpulsechecker.notificationpolicy.NotificationSeverity;
import jakarta.validation.constraints.NotNull;

public record NotificationPolicyRouteRequest(
        @NotNull NotificationSeverity severity,
        @NotNull Boolean webhookEnabled
) {
}
