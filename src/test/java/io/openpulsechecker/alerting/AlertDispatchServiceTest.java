package io.openpulsechecker.alerting;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openpulsechecker.notificationpolicy.NotificationChannel;
import io.openpulsechecker.notificationpolicy.NotificationSeverity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertDispatchServiceTest {

    @Test
    void notifierFailureDoesNotBreakDispatchFlow() {
        AlertNotifier failing = mock(AlertNotifier.class);
        AlertNotifier healthy = mock(AlertNotifier.class);
        NotificationPolicyResolver resolver = mock(NotificationPolicyResolver.class);
        DispatchedAlertRepository dispatchedAlertRepository = mock(DispatchedAlertRepository.class);

        when(failing.channel()).thenReturn(NotificationChannel.WEBHOOK);
        when(healthy.channel()).thenReturn(NotificationChannel.WEBHOOK);
        doThrow(new RuntimeException("boom")).when(failing).notify(any(), any());

        AlertEvent event = new AlertEvent(AlertEventType.INCIDENT_OPENED, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "down");
        when(resolver.resolve(event)).thenReturn(Optional.of(new NotificationDispatchPlan(
                null, NotificationSeverity.CRITICAL, 0, 0, EnumSet.of(NotificationChannel.WEBHOOK), null)));

        AlertDispatchService service = new AlertDispatchService(
                List.of(failing, healthy), resolver, dispatchedAlertRepository,
                Clock.fixed(Instant.parse("2026-02-27T07:00:00Z"), ZoneOffset.UTC));

        assertDoesNotThrow(() -> service.dispatch(event));
        verify(healthy).notify(any(), any());
    }
}
