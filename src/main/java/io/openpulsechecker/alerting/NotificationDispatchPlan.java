package io.openpulsechecker.alerting;

import io.openpulsechecker.notificationpolicy.NotificationPolicyModel;
import io.openpulsechecker.notificationpolicy.NotificationSeverity;
import java.util.Set;
import java.util.UUID;

public record NotificationDispatchPlan(
        UUID policyId,
        NotificationSeverity severity,
        int cooldownSeconds,
        int dedupSeconds,
        Set<io.openpulsechecker.notificationpolicy.NotificationChannel> channels,
        NotificationPolicyModel policy
) {
}
