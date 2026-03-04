package io.openpulsechecker.alerting;

import io.micrometer.core.instrument.MeterRegistry;
import io.openpulsechecker.config.TeamsAlertingProperties;
import io.openpulsechecker.notificationpolicy.NotificationChannel;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "openpulse.alerting.teams", name = "enabled", havingValue = "true")
public class TeamsAlertNotifier extends AbstractAlertNotifier {

    private final TeamsAlertingProperties properties;

    public TeamsAlertNotifier(RestClient.Builder restClientBuilder,
                              TeamsAlertingProperties properties,
                              DispatchedAlertRepository dispatchedAlertRepository,
                              AlertDeadLetterRepository deadLetterRepository,
                              MeterRegistry meterRegistry,
                              Clock clock) {
        super(restClientBuilder, dispatchedAlertRepository, deadLetterRepository, meterRegistry, clock);
        this.properties = properties;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.TEAMS;
    }

    @Override
    protected EndpointConfig endpointConfig() {
        return new EndpointConfig(properties.webhookUrl(), Map.of(), properties.maxAttempts(), properties.initialBackoffMs());
    }

    @Override
    protected Object payload(AlertEvent event, NotificationDispatchPlan plan) {
        return Map.of("text", String.format("[%s] %s (%s) - %s", plan.severity(), event.type(), event.monitorId(), event.reason()));
    }
}
