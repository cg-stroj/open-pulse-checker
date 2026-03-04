package io.openpulsechecker.alerting;

import io.micrometer.core.instrument.MeterRegistry;
import io.openpulsechecker.config.EmailAlertingProperties;
import io.openpulsechecker.notificationpolicy.NotificationChannel;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "openpulse.alerting.email", name = "enabled", havingValue = "true")
public class EmailAlertNotifier extends AbstractAlertNotifier {

    private final EmailAlertingProperties properties;

    public EmailAlertNotifier(RestClient.Builder restClientBuilder,
                              EmailAlertingProperties properties,
                              DispatchedAlertRepository dispatchedAlertRepository,
                              AlertDeadLetterRepository deadLetterRepository,
                              MeterRegistry meterRegistry,
                              Clock clock) {
        super(restClientBuilder, dispatchedAlertRepository, deadLetterRepository, meterRegistry, clock);
        this.properties = properties;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    protected EndpointConfig endpointConfig() {
        Map<String, String> headers = new HashMap<>();
        if (properties.bearerToken() != null && !properties.bearerToken().isBlank()) {
            headers.put("Authorization", "Bearer " + properties.bearerToken());
        }
        return new EndpointConfig(properties.url(), headers, properties.maxAttempts(), properties.initialBackoffMs());
    }

    @Override
    protected Object payload(AlertEvent event, NotificationDispatchPlan plan) {
        return Map.of(
                "to", properties.to(),
                "subject", String.format("OpenPulse %s %s", plan.severity(), event.type()),
                "body", String.format("Monitor=%s Incident=%s Reason=%s", event.monitorId(), event.incidentId(), event.reason())
        );
    }
}
