package io.openpulsechecker.alerting;

public interface AlertNotifier {
    io.openpulsechecker.notificationpolicy.NotificationChannel channel();
    void notify(AlertEvent event, NotificationDispatchPlan plan);
}
