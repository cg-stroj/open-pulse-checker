package io.openpulsechecker.alerting;

public interface AlertNotifier {
    void notify(AlertEvent event);
}
