package io.openpulsechecker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.domain.IncidentState;
import io.openpulsechecker.persistence.IncidentEntity;
import io.openpulsechecker.persistence.IncidentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private IncidentService incidentService;

    @Test
    void opensIncidentWhenDownAndNoOpenIncident() {
        UUID monitorId = UUID.randomUUID();
        Instant checkedAt = Instant.now();
        when(incidentRepository.findTopByMonitorIdAndStateOrderByOpenedAtDesc(monitorId, IncidentState.OPEN))
                .thenReturn(Optional.empty());

        incidentService.applyTransition(monitorId, CheckStatus.DOWN, checkedAt, "HTTP status 500");

        ArgumentCaptor<IncidentEntity> captor = ArgumentCaptor.forClass(IncidentEntity.class);
        verify(incidentRepository).save(captor.capture());
        IncidentEntity saved = captor.getValue();
        assertEquals(IncidentState.OPEN, saved.getState());
        assertEquals(checkedAt, saved.getOpenedAt());
        assertEquals("HTTP status 500", saved.getReason());
    }

    @Test
    void resolvesOpenIncidentWhenRecovered() {
        UUID monitorId = UUID.randomUUID();
        Instant checkedAt = Instant.now();
        IncidentEntity existing = new IncidentEntity();
        existing.setMonitorId(monitorId);
        existing.setState(IncidentState.OPEN);
        existing.setOpenedAt(checkedAt.minusSeconds(30));
        existing.setReason("timeout");

        when(incidentRepository.findTopByMonitorIdAndStateOrderByOpenedAtDesc(monitorId, IncidentState.OPEN))
                .thenReturn(Optional.of(existing));

        incidentService.applyTransition(monitorId, CheckStatus.UP, checkedAt, null);

        verify(incidentRepository).save(existing);
        assertEquals(IncidentState.RESOLVED, existing.getState());
        assertEquals(checkedAt, existing.getResolvedAt());
    }

    @Test
    void doesNothingWhenDownAndIncidentAlreadyOpen() {
        UUID monitorId = UUID.randomUUID();
        IncidentEntity existing = new IncidentEntity();
        existing.setState(IncidentState.OPEN);

        when(incidentRepository.findTopByMonitorIdAndStateOrderByOpenedAtDesc(monitorId, IncidentState.OPEN))
                .thenReturn(Optional.of(existing));

        incidentService.applyTransition(monitorId, CheckStatus.DOWN, Instant.now(), "still down");

        verify(incidentRepository, never()).save(any());
    }
}
