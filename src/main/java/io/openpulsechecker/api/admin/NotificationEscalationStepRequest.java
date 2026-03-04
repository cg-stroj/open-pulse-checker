package io.openpulsechecker.api.admin;

import io.openpulsechecker.notificationpolicy.NotificationChannel;
import io.openpulsechecker.notificationpolicy.NotificationSeverity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record NotificationEscalationStepRequest(
        @Min(1) int stepOrder,
        @Min(0) int afterSeconds,
        @NotNull NotificationSeverity minSeverity,
        @NotEmpty Set<NotificationChannel> channels
) {
}
