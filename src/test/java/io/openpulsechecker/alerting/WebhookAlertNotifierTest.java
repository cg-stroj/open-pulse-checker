package io.openpulsechecker.alerting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openpulsechecker.config.AlertingProperties;
import io.openpulsechecker.notificationpolicy.NotificationChannel;
import io.openpulsechecker.notificationpolicy.NotificationSeverity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class WebhookAlertNotifierTest {

    @Test
    void retriesAndStoresIdempotencyOnSuccess() {
        RestClient.Builder builder = org.mockito.Mockito.mock(RestClient.Builder.class);
        RestClient restClient = org.mockito.Mockito.mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = org.mockito.Mockito.mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = org.mockito.Mockito.mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);
        DispatchedAlertRepository repo = org.mockito.Mockito.mock(DispatchedAlertRepository.class);
        AlertDeadLetterRepository dlqRepo = org.mockito.Mockito.mock(AlertDeadLetterRepository.class);

        when(builder.build()).thenReturn(restClient);
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity())
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(ResponseEntity.ok().build());
        when(repo.existsById(anyString())).thenReturn(false);

        WebhookAlertNotifier notifier = new WebhookAlertNotifier(
                builder,
                new AlertingProperties(true, "https://example.test/webhook", 2, 1),
                repo,
                dlqRepo,
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-02-26T22:00:00Z"), ZoneOffset.UTC));

        notifier.notify(new AlertEvent(AlertEventType.INCIDENT_OPENED, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "down"), plan());

        verify(responseSpec, times(2)).toBodilessEntity();
        verify(repo).save(any(DispatchedAlertEntity.class));
        verify(dlqRepo, never()).save(any(AlertDeadLetterEntity.class));
    }

    @Test
    void exhaustedRetriesInsertIntoDlq() {
        RestClient.Builder builder = org.mockito.Mockito.mock(RestClient.Builder.class);
        RestClient restClient = org.mockito.Mockito.mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = org.mockito.Mockito.mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = org.mockito.Mockito.mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);
        DispatchedAlertRepository repo = org.mockito.Mockito.mock(DispatchedAlertRepository.class);
        AlertDeadLetterRepository dlqRepo = org.mockito.Mockito.mock(AlertDeadLetterRepository.class);

        when(builder.build()).thenReturn(restClient);
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("perm-fail"));
        when(repo.existsById(anyString())).thenReturn(false);

        WebhookAlertNotifier notifier = new WebhookAlertNotifier(
                builder,
                new AlertingProperties(true, "https://example.test/webhook", 2, 1),
                repo,
                dlqRepo,
                new SimpleMeterRegistry(),
                Clock.systemUTC());

        notifier.notify(new AlertEvent(AlertEventType.INCIDENT_OPENED, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "down"), plan());

        verify(dlqRepo).save(any(AlertDeadLetterEntity.class));
    }

    @Test
    void duplicateIdempotencyKeySkipsDelivery() {
        RestClient.Builder builder = org.mockito.Mockito.mock(RestClient.Builder.class);
        DispatchedAlertRepository repo = org.mockito.Mockito.mock(DispatchedAlertRepository.class);
        AlertDeadLetterRepository dlqRepo = org.mockito.Mockito.mock(AlertDeadLetterRepository.class);
        when(builder.build()).thenReturn(org.mockito.Mockito.mock(RestClient.class));
        when(repo.existsById(anyString())).thenReturn(true);

        WebhookAlertNotifier notifier = new WebhookAlertNotifier(
                builder,
                new AlertingProperties(true, "https://example.test/webhook", 2, 1),
                repo,
                dlqRepo,
                new SimpleMeterRegistry(),
                Clock.systemUTC());

        notifier.notify(new AlertEvent(AlertEventType.INCIDENT_OPENED, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "down"), plan());

        verify(repo, never()).save(any());
    }

    private NotificationDispatchPlan plan() {
        return new NotificationDispatchPlan(null, NotificationSeverity.CRITICAL, 0, 0, EnumSet.of(NotificationChannel.WEBHOOK), null);
    }
}
