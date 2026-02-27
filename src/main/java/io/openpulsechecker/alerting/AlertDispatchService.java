package io.openpulsechecker.alerting;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AlertDispatchService {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatchService.class);

    private final List<AlertNotifier> notifiers;
    private final NotificationPolicyResolver notificationPolicyResolver;
    private final DispatchedAlertRepository dispatchedAlertRepository;
    private final Clock clock;

    public AlertDispatchService(List<AlertNotifier> notifiers,
                                NotificationPolicyResolver notificationPolicyResolver,
                                DispatchedAlertRepository dispatchedAlertRepository,
                                Clock clock) {
        this.notifiers = notifiers;
        this.notificationPolicyResolver = notificationPolicyResolver;
        this.dispatchedAlertRepository = dispatchedAlertRepository;
        this.clock = clock;
    }

    public void dispatch(AlertEvent event) {
        var optionalPlan = notificationPolicyResolver.resolve(event);
        if (optionalPlan.isEmpty()) {
            return;
        }

        NotificationDispatchPlan plan = optionalPlan.get();
        Instant now = Instant.now(clock);

        for (AlertNotifier notifier : notifiers) {
            if (!plan.channels().contains(notifier.channel())) {
                continue;
            }
            if (isSuppressed(event, plan, notifier.channel().name(), now)) {
                continue;
            }
            try {
                notifier.notify(event, plan);
            } catch (Exception ex) {
                log.error("Notifier {} failed for event {}", notifier.getClass().getSimpleName(), event.type(), ex);
            }
        }
    }

    private boolean isSuppressed(AlertEvent event, NotificationDispatchPlan plan, String channel, Instant now) {
        if (plan.dedupSeconds() > 0) {
            Instant cutoff = now.minusSeconds(plan.dedupSeconds());
            long recentSameIncident = dispatchedAlertRepository.countByMonitorIdAndIncidentIdAndSeverityAndChannelAndCreatedAtGreaterThanEqual(
                    event.monitorId(), event.incidentId(), plan.severity().name(), channel, cutoff);
            if (recentSameIncident > 0) {
                return true;
            }
        }
        if (plan.cooldownSeconds() > 0) {
            Instant cutoff = now.minusSeconds(plan.cooldownSeconds());
            long recentByMonitorSeverity = dispatchedAlertRepository.countByMonitorIdAndSeverityAndChannelAndCreatedAtGreaterThanEqual(
                    event.monitorId(), plan.severity().name(), channel, cutoff);
            return recentByMonitorSeverity > 0;
        }
        return false;
    }
}
