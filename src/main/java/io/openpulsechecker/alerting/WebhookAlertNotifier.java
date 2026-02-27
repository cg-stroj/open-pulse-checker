package io.openpulsechecker.alerting;

import io.micrometer.core.instrument.MeterRegistry;
import io.openpulsechecker.config.AlertingProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "openpulse.alerting.webhook", name = "enabled", havingValue = "true")
public class WebhookAlertNotifier implements AlertNotifier {

    private final RestClient restClient;
    private final AlertingProperties alertingProperties;
    private final DispatchedAlertRepository dispatchedAlertRepository;
    private final AlertDeadLetterRepository deadLetterRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public WebhookAlertNotifier(
            RestClient.Builder restClientBuilder,
            AlertingProperties alertingProperties,
            DispatchedAlertRepository dispatchedAlertRepository,
            AlertDeadLetterRepository deadLetterRepository,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.restClient = restClientBuilder.build();
        this.alertingProperties = alertingProperties;
        this.dispatchedAlertRepository = dispatchedAlertRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Override
    public io.openpulsechecker.notificationpolicy.NotificationChannel channel() {
        return io.openpulsechecker.notificationpolicy.NotificationChannel.WEBHOOK;
    }

    @Override
    public void notify(AlertEvent event, NotificationDispatchPlan plan) {
        String idempotencyKey = buildIdempotencyKey(event);
        if (dispatchedAlertRepository.existsById(idempotencyKey)) {
            return;
        }

        int maxAttempts = Math.max(1, alertingProperties.maxAttempts());
        long backoffMs = Math.max(25, alertingProperties.initialBackoffMs());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                RestClient.RequestBodySpec request = restClient.post()
                        .uri(alertingProperties.url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", idempotencyKey);
                request.body(event);
                request.retrieve().toBodilessEntity();

                DispatchedAlertEntity saved = new DispatchedAlertEntity();
                saved.setIdempotencyKey(idempotencyKey);
                saved.setEventType(event.type().name());
                saved.setMonitorId(event.monitorId());
                saved.setIncidentId(event.incidentId());
                saved.setSeverity(plan.severity().name());
                saved.setChannel(channel().name());
                saved.setPolicyId(plan.policyId());
                saved.setCreatedAt(Instant.now(clock));
                dispatchedAlertRepository.save(saved);
                meterRegistry.counter("openpulse.alerts.sent").increment();
                return;
            } catch (Exception ex) {
                if (attempt >= maxAttempts) {
                    meterRegistry.counter("openpulse.alerts.failed").increment();
                    AlertDeadLetterEntity dlq = new AlertDeadLetterEntity();
                    dlq.setEventType(event.type().name());
                    dlq.setMonitorId(event.monitorId());
                    dlq.setIncidentId(event.incidentId());
                    dlq.setPayload(event.reason());
                    dlq.setFailureReason(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                    dlq.setAttempts(attempt);
                    dlq.setCreatedAt(Instant.now(clock));
                    deadLetterRepository.save(dlq);
                    meterRegistry.counter("openpulse.alerts.dlq").increment();
                    return;
                }
                sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 5_000L);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", interruptedException);
        }
    }

    static String buildIdempotencyKey(AlertEvent event) {
        String payload = event.type() + "|" + event.monitorId() + "|" + event.incidentId() + "|" + event.occurredAt();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build idempotency key", e);
        }
    }
}
