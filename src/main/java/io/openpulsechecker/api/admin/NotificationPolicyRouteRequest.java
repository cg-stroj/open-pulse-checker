package io.openpulsechecker.api.admin;

import io.openpulsechecker.notificationpolicy.NotificationChannel;
import io.openpulsechecker.notificationpolicy.NotificationSeverity;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record NotificationPolicyRouteRequest(
        @NotNull NotificationSeverity severity,
        @NotEmpty Set<NotificationChannel> channels
) {
}
