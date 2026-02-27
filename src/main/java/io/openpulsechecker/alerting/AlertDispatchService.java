package io.openpulsechecker.alerting;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
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
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public AlertDispatchService(List<AlertNotifier> notifiers,
                                NotificationPolicyResolver notificationPolicyResolver,
                                DispatchedAlertRepository dispatchedAlertRepository,
                                MeterRegistry meterRegistry,
                                Clock clock) {
        this.notifiers = notifiers;
        this.notificationPolicyResolver = notificationPolicyResolver;
        this.dispatchedAlertRepository = dispatchedAlertRepository;
        this.meterRegistry = meterRegistry;
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
                meterRegistry.counter("openpulse.alerts.dispatch.attempts", "channel", notifier.channel().name(), "outcome", "suppressed")
                        .increment();
                continue;
            }

            Instant dispatchStart = Instant.now(clock);
            try {
                notifier.notify(event, plan);
                recordDispatchMetrics(notifier.channel().name(), "success", dispatchStart, event.occurredAt());
            } catch (Exception ex) {
                recordDispatchMetrics(notifier.channel().name(), "failed", dispatchStart, event.occurredAt());
                log.error("Notifier {} failed for event {}", notifier.getClass().getSimpleName(), event.type(), ex);
            }
        }
    }

    private void recordDispatchMetrics(String channel, String outcome, Instant dispatchStart, Instant occurredAt) {
        meterRegistry.counter("openpulse.alerts.dispatch.attempts", "channel", channel, "outcome", outcome).increment();
        Timer.builder("openpulse.alerts.dispatch.latency")
                .description("Per-notifier dispatch call latency")
                .tag("channel", channel)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(Duration.between(dispatchStart, Instant.now(clock)));
        Timer.builder("openpulse.alerts.delivery.delay")
                .description("Delay between event occurrence and notifier dispatch completion")
                .tag("channel", channel)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(Duration.between(occurredAt, Instant.now(clock)));
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
