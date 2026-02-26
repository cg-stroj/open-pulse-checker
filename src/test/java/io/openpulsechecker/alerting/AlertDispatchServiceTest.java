package io.openpulsechecker.alerting;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertDispatchServiceTest {

    @Test
    void notifierFailureDoesNotBreakDispatchFlow() {
        AlertNotifier failing = mock(AlertNotifier.class);
        AlertNotifier healthy = mock(AlertNotifier.class);
        doThrow(new RuntimeException("boom")).when(failing).notify(org.mockito.ArgumentMatchers.any());

        AlertDispatchService service = new AlertDispatchService(List.of(failing, healthy));
        AlertEvent event = new AlertEvent(AlertEventType.INCIDENT_OPENED, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "down");

        assertDoesNotThrow(() -> service.dispatch(event));
        verify(healthy).notify(event);
    }
}
