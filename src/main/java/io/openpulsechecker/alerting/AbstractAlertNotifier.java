package io.openpulsechecker.alerting;

import io.micrometer.core.instrument.MeterRegistry;
import io.openpulsechecker.notificationpolicy.NotificationChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

abstract class AbstractAlertNotifier implements AlertNotifier {

    private final RestClient restClient;
    private final DispatchedAlertRepository dispatchedAlertRepository;
    private final AlertDeadLetterRepository deadLetterRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    protected AbstractAlertNotifier(RestClient.Builder restClientBuilder,
                                    DispatchedAlertRepository dispatchedAlertRepository,
                                    AlertDeadLetterRepository deadLetterRepository,
                                    MeterRegistry meterRegistry,
                                    Clock clock) {
        this.restClient = restClientBuilder.build();
        this.dispatchedAlertRepository = dispatchedAlertRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    protected abstract EndpointConfig endpointConfig();

    protected abstract Object payload(AlertEvent event, NotificationDispatchPlan plan);

    @Override
    public void notify(AlertEvent event, NotificationDispatchPlan plan) {
        EndpointConfig cfg = endpointConfig();
        String idempotencyKey = buildIdempotencyKey(event, channel());
        if (dispatchedAlertRepository.existsById(idempotencyKey)) {
            return;
        }

        int maxAttempts = Math.max(1, cfg.maxAttempts());
        long backoffMs = Math.max(25, cfg.initialBackoffMs());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                RestClient.RequestBodySpec request = restClient.post()
                        .uri(cfg.url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", idempotencyKey);
                for (Map.Entry<String, String> header : cfg.headers().entrySet()) {
                    request.header(header.getKey(), header.getValue());
                }
                request.body(payload(event, plan));
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
                meterRegistry.counter("openpulse.alerts.sent", "channel", channel().name()).increment();
                return;
            } catch (Exception ex) {
                if (attempt >= maxAttempts) {
                    meterRegistry.counter("openpulse.alerts.failed", "channel", channel().name()).increment();
                    AlertDeadLetterEntity dlq = new AlertDeadLetterEntity();
                    dlq.setEventType(event.type().name());
                    dlq.setMonitorId(event.monitorId());
                    dlq.setIncidentId(event.incidentId());
                    dlq.setPayload(SecretSanitizer.sanitize(event.reason()));
                    dlq.setFailureReason(SecretSanitizer.sanitize(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                    dlq.setAttempts(attempt);
                    dlq.setCreatedAt(Instant.now(clock));
                    deadLetterRepository.save(dlq);
                    meterRegistry.counter("openpulse.alerts.dlq", "channel", channel().name()).increment();
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

    static String buildIdempotencyKey(AlertEvent event, NotificationChannel channel) {
        String payload = channel.name() + "|" + event.type() + "|" + event.monitorId() + "|" + event.incidentId() + "|" + event.occurredAt();
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

    protected record EndpointConfig(String url, Map<String, String> headers, int maxAttempts, long initialBackoffMs) {
    }
}
