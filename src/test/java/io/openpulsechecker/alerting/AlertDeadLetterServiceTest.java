package io.openpulsechecker.alerting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openpulsechecker.audit.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertDeadLetterServiceTest {

    @Test
    void replayFlowMarksItemAndDispatches() {
        AlertDeadLetterRepository repo = org.mockito.Mockito.mock(AlertDeadLetterRepository.class);
        AlertDispatchService dispatch = org.mockito.Mockito.mock(AlertDispatchService.class);
        AuditService audit = org.mockito.Mockito.mock(AuditService.class);
        AlertDeadLetterService service = new AlertDeadLetterService(
                repo,
                dispatch,
                audit,
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-02-26T22:00:00Z"), ZoneOffset.UTC));

        UUID id = UUID.randomUUID();
        AlertDeadLetterEntity entity = new AlertDeadLetterEntity();
        entity.setId(id);
        entity.setEventType(AlertEventType.INCIDENT_OPENED.name());
        entity.setMonitorId(UUID.randomUUID());
        entity.setPayload("down");
        entity.setAttempts(3);
        entity.setFailureReason("boom");
        entity.setCreatedAt(Instant.parse("2026-02-26T21:00:00Z"));
        when(repo.findById(id)).thenReturn(Optional.of(entity));

        service.replay(id);

        verify(dispatch).dispatch(org.mockito.ArgumentMatchers.any(AlertEvent.class));
        verify(repo).save(entity);
        assertEquals("SUCCESS", entity.getReplayResult());
    }
}
