package io.openpulsechecker.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openpulsechecker.audit.AuditService;
import io.openpulsechecker.domain.IncidentState;
import io.openpulsechecker.persistence.IncidentEntity;
import io.openpulsechecker.persistence.IncidentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentAdminServiceTest {

    @Mock private IncidentRepository incidentRepository;
    @Mock private IncidentManualEventRepository incidentManualEventRepository;
    @Mock private AuditService auditService;

    private Clock clock;
    private IncidentAdminService incidentAdminService;

    @BeforeEach
    void setup() {
        clock = Clock.fixed(Instant.parse("2026-02-27T15:00:00Z"), ZoneOffset.UTC);
        incidentAdminService = new IncidentAdminService(incidentRepository, incidentManualEventRepository, auditService, clock);
    }

    @Test
    void acknowledgeTransitionsOpenToAcknowledgedAndWritesAuditAndEvent() {
        IncidentEntity incident = new IncidentEntity();
        UUID incidentId = UUID.randomUUID();
        incident.setId(incidentId);
        incident.setState(IncidentState.OPEN);

        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(incident)).thenReturn(incident);

        incidentAdminService.acknowledge(incidentId, "on-call acknowledged");

        assertEquals(IncidentState.ACKNOWLEDGED, incident.getState());
        verify(auditService).log("incident.acknowledge", "incident:" + incidentId, "SUCCESS", "on-call acknowledged");

        ArgumentCaptor<IncidentManualEventEntity> captor = ArgumentCaptor.forClass(IncidentManualEventEntity.class);
        verify(incidentManualEventRepository).save(captor.capture());
        assertEquals(IncidentManualAction.ACKNOWLEDGED, captor.getValue().getAction());
        assertEquals(IncidentState.OPEN, captor.getValue().getFromState());
        assertEquals(IncidentState.ACKNOWLEDGED, captor.getValue().getToState());
    }

    @Test
    void resolveRejectsResolvedIncident() {
        IncidentEntity incident = new IncidentEntity();
        UUID incidentId = UUID.randomUUID();
        incident.setId(incidentId);
        incident.setState(IncidentState.RESOLVED);
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));

        assertThrows(IllegalArgumentException.class,
                () -> incidentAdminService.resolve(incidentId, "manual close"));
    }

    @Test
    void reopenTransitionsResolvedToOpenAndClearsResolvedAt() {
        IncidentEntity incident = new IncidentEntity();
        UUID incidentId = UUID.randomUUID();
        incident.setId(incidentId);
        incident.setState(IncidentState.RESOLVED);
        incident.setResolvedAt(Instant.parse("2026-02-27T14:00:00Z"));
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(incident)).thenReturn(incident);

        incidentAdminService.reopen(incidentId, "false recovery");

        assertEquals(IncidentState.OPEN, incident.getState());
        assertEquals(null, incident.getResolvedAt());
        verify(auditService).log("incident.reopen", "incident:" + incidentId, "SUCCESS", "false recovery");
        verify(incidentManualEventRepository).save(any(IncidentManualEventEntity.class));
    }

    @Test
    void annotationRequiresReason() {
        IncidentEntity incident = new IncidentEntity();
        UUID incidentId = UUID.randomUUID();
        incident.setId(incidentId);
        incident.setState(IncidentState.OPEN);
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));

        assertThrows(IllegalArgumentException.class,
                () -> incidentAdminService.addAnnotation(incidentId, " "));
    }
}
