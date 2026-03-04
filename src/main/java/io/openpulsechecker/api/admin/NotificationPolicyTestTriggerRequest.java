package io.openpulsechecker.api.admin;

import io.openpulsechecker.notificationpolicy.NotificationChannel;
import java.util.Set;

public record NotificationPolicyTestTriggerRequest(Set<NotificationChannel> channels, String reason) {
}
