package io.openpulsechecker.alerting;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AlertDispatchService {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatchService.class);

    private final List<AlertNotifier> notifiers;

    public AlertDispatchService(List<AlertNotifier> notifiers) {
        this.notifiers = notifiers;
    }

    public void dispatch(AlertEvent event) {
        for (AlertNotifier notifier : notifiers) {
            try {
                notifier.notify(event);
            } catch (Exception ex) {
                log.error("Notifier {} failed for event {}", notifier.getClass().getSimpleName(), event.type(), ex);
            }
        }
    }
}
