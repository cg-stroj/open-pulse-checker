package io.openpulsechecker.alerting;

import io.micrometer.core.instrument.MeterRegistry;
import io.openpulsechecker.config.DiscordAlertingProperties;
import io.openpulsechecker.notificationpolicy.NotificationChannel;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "openpulse.alerting.discord", name = "enabled", havingValue = "true")
public class DiscordAlertNotifier extends AbstractAlertNotifier {

    private final DiscordAlertingProperties properties;

    public DiscordAlertNotifier(RestClient.Builder restClientBuilder,
                                DiscordAlertingProperties properties,
                                DispatchedAlertRepository dispatchedAlertRepository,
                                AlertDeadLetterRepository deadLetterRepository,
                                MeterRegistry meterRegistry,
                                Clock clock) {
        super(restClientBuilder, dispatchedAlertRepository, deadLetterRepository, meterRegistry, clock);
        this.properties = properties;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.DISCORD;
    }

    @Override
    protected EndpointConfig endpointConfig() {
        return new EndpointConfig(properties.webhookUrl(), Map.of(), properties.maxAttempts(), properties.initialBackoffMs());
    }

    @Override
    protected Object payload(AlertEvent event, NotificationDispatchPlan plan) {
        return Map.of("content", String.format("[%s] %s (%s) - %s", plan.severity(), event.type(), event.monitorId(), event.reason()));
    }
}
