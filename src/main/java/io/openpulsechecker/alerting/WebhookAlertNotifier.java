package io.openpulsechecker.alerting;

import io.openpulsechecker.config.AlertingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "openpulse.alerting.webhook", name = "enabled", havingValue = "true")
public class WebhookAlertNotifier implements AlertNotifier {

    private final RestClient restClient;
    private final AlertingProperties alertingProperties;

    public WebhookAlertNotifier(RestClient.Builder restClientBuilder, AlertingProperties alertingProperties) {
        this.restClient = restClientBuilder.build();
        this.alertingProperties = alertingProperties;
    }

    @Override
    public void notify(AlertEvent event) {
        restClient.post()
                .uri(alertingProperties.url())
                .contentType(MediaType.APPLICATION_JSON)
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
