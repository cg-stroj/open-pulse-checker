package io.openpulsechecker.alerting;

import io.micrometer.core.instrument.MeterRegistry;
import io.openpulsechecker.config.TelegramAlertingProperties;
import io.openpulsechecker.notificationpolicy.NotificationChannel;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "openpulse.alerting.telegram", name = "enabled", havingValue = "true")
public class TelegramAlertNotifier extends AbstractAlertNotifier {

    private final TelegramAlertingProperties properties;

    public TelegramAlertNotifier(RestClient.Builder restClientBuilder,
                                 TelegramAlertingProperties properties,
                                 DispatchedAlertRepository dispatchedAlertRepository,
                                 AlertDeadLetterRepository deadLetterRepository,
                                 MeterRegistry meterRegistry,
                                 Clock clock) {
        super(restClientBuilder, dispatchedAlertRepository, deadLetterRepository, meterRegistry, clock);
        this.properties = properties;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.TELEGRAM;
    }

    @Override
    protected EndpointConfig endpointConfig() {
        String base = properties.apiBaseUrl() == null || properties.apiBaseUrl().isBlank()
                ? "https://api.telegram.org"
                : properties.apiBaseUrl();
        String url = String.format("%s/bot%s/sendMessage", base, properties.botToken());
        return new EndpointConfig(url, Map.of(), properties.maxAttempts(), properties.initialBackoffMs());
    }

    @Override
    protected Object payload(AlertEvent event, NotificationDispatchPlan plan) {
        return Map.of(
                "chat_id", properties.chatId(),
                "text", String.format("[%s] %s (%s) - %s", plan.severity(), event.type(), event.monitorId(), event.reason())
        );
    }
}
