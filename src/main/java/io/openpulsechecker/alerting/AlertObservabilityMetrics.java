package io.openpulsechecker.alerting;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class AlertObservabilityMetrics {

    public AlertObservabilityMetrics(AlertDeadLetterRepository deadLetterRepository,
                                     MeterRegistry meterRegistry,
                                     Clock clock) {
        Gauge.builder("openpulse.alerts.dlq.backlog", deadLetterRepository, AlertDeadLetterRepository::countByReplayedAtIsNull)
                .description("Current number of unreplayed dead-letter alerts")
                .register(meterRegistry);

        Gauge.builder("openpulse.alerts.dlq.oldest.age.seconds", () -> oldestAgeSeconds(deadLetterRepository, clock))
                .description("Age in seconds of the oldest unreplayed dead-letter alert")
                .register(meterRegistry);

        meterRegistry.counter("openpulse.alerts.dispatch.attempts", "channel", "bootstrap", "outcome", "success");
        meterRegistry.counter("openpulse.alerts.dispatch.attempts", "channel", "bootstrap", "outcome", "failed");

        Timer.builder("openpulse.alerts.dispatch.latency")
                .tag("outcome", "success")
                .description("Dispatch latency in seconds for successful alert dispatches")
                .register(meterRegistry);

        Timer.builder("openpulse.alerts.delivery.delay")
                .tag("outcome", "success")
                .description("Delivery delay in seconds between incident update and successful dispatch")
                .register(meterRegistry);
    }

    private static double oldestAgeSeconds(AlertDeadLetterRepository repository, Clock clock) {
        return repository.findFirstByReplayedAtIsNullOrderByCreatedAtAsc()
                .map(AlertDeadLetterEntity::getCreatedAt)
                .map(createdAt -> Math.max(0, Duration.between(createdAt, Instant.now(clock)).toSeconds()))
                .orElse(0L);
    }
}
