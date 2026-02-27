package io.openpulsechecker.api.admin;

import io.openpulsechecker.notificationpolicy.NotificationSeverity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record NotificationEscalationStepRequest(
        @Min(1) int stepOrder,
        @Min(0) int afterSeconds,
        @NotNull NotificationSeverity minSeverity,
        @NotNull Boolean webhookEnabled
) {
}
