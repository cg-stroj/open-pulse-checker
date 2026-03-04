package io.openpulsechecker.alerting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openpulsechecker.config.DiscordAlertingProperties;
import io.openpulsechecker.config.EmailAlertingProperties;
import io.openpulsechecker.config.SlackAlertingProperties;
import io.openpulsechecker.config.TeamsAlertingProperties;
import io.openpulsechecker.config.TelegramAlertingProperties;
import io.openpulsechecker.notificationpolicy.NotificationChannel;
import io.openpulsechecker.notificationpolicy.NotificationSeverity;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class MultiChannelAlertNotifierTest {

    @Test
    void allChannelNotifiersSendViaHttp() {
        runSuccess(new SlackAlertNotifier(builder(), new SlackAlertingProperties(true, "https://example/slack", 2, 1), repo(), dlq(), new SimpleMeterRegistry(), Clock.systemUTC()));
        runSuccess(new DiscordAlertNotifier(builder(), new DiscordAlertingProperties(true, "https://example/discord", 2, 1), repo(), dlq(), new SimpleMeterRegistry(), Clock.systemUTC()));
        runSuccess(new TeamsAlertNotifier(builder(), new TeamsAlertingProperties(true, "https://example/teams", 2, 1), repo(), dlq(), new SimpleMeterRegistry(), Clock.systemUTC()));
        runSuccess(new EmailAlertNotifier(builder(), new EmailAlertingProperties(true, "https://example/email", "ops@example.com", "token", 2, 1), repo(), dlq(), new SimpleMeterRegistry(), Clock.systemUTC()));
        runSuccess(new TelegramAlertNotifier(builder(), new TelegramAlertingProperties(true, "123:token", "-1001", "https://api.telegram.org", 2, 1), repo(), dlq(), new SimpleMeterRegistry(), Clock.systemUTC()));
    }

    private void runSuccess(AlertNotifier notifier) {
        notifier.notify(new AlertEvent(AlertEventType.INCIDENT_OPENED, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "test"), plan(notifier.channel()));
    }

    private NotificationDispatchPlan plan(NotificationChannel channel) {
        return new NotificationDispatchPlan(null, NotificationSeverity.CRITICAL, 0, 0, EnumSet.of(channel), null);
    }

    private RestClient.Builder builder() {
        RestClient.Builder builder = org.mockito.Mockito.mock(RestClient.Builder.class);
        RestClient restClient = org.mockito.Mockito.mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = org.mockito.Mockito.mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = org.mockito.Mockito.mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);

        when(builder.build()).thenReturn(restClient);
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
        return builder;
    }

    private DispatchedAlertRepository repo() {
        DispatchedAlertRepository repo = org.mockito.Mockito.mock(DispatchedAlertRepository.class);
        when(repo.existsById(anyString())).thenReturn(false);
        return repo;
    }

    private AlertDeadLetterRepository dlq() {
        return org.mockito.Mockito.mock(AlertDeadLetterRepository.class);
    }
}
