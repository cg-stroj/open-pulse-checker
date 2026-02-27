package io.openpulsechecker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openpulsechecker.alerting.AlertDispatchService;
import io.openpulsechecker.alerting.AlertEvent;
import io.openpulsechecker.alerting.AlertEventType;
import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.domain.IncidentState;
import io.openpulsechecker.maintenance.MaintenanceEvaluation;
import io.openpulsechecker.maintenance.MaintenancePolicy;
import io.openpulsechecker.maintenance.MaintenanceWindowService;
import io.openpulsechecker.persistence.IncidentEntity;
import io.openpulsechecker.persistence.IncidentRepository;
import java.time.Instant;
import java.util.EnumSet;
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
    @Mock
    private AlertDispatchService alertDispatchService;
    @Mock
    private MaintenanceWindowService maintenanceWindowService;

    @InjectMocks
    private IncidentService incidentService;

    @Test
    void opensIncidentWhenDownAndNoOpenIncident() {
        UUID monitorId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        Instant checkedAt = Instant.now();
        when(maintenanceWindowService.evaluate(monitorId, checkedAt)).thenReturn(MaintenanceEvaluation.inactive());
        when(incidentRepository.findTopByMonitorIdAndStateInOrderByOpenedAtDesc(monitorId, EnumSet.of(IncidentState.OPEN, IncidentState.ACKNOWLEDGED)))
                .thenReturn(Optional.empty());
        when(incidentRepository.save(any(IncidentEntity.class))).thenAnswer(inv -> {
            IncidentEntity incident = inv.getArgument(0);
            incident.setId(incidentId);
            return incident;
        });

        incidentService.applyTransition(monitorId, CheckStatus.DOWN, checkedAt, "HTTP status 500");

        ArgumentCaptor<IncidentEntity> captor = ArgumentCaptor.forClass(IncidentEntity.class);
        verify(incidentRepository).save(captor.capture());
        IncidentEntity saved = captor.getValue();
        assertEquals(IncidentState.OPEN, saved.getState());
        assertEquals(checkedAt, saved.getOpenedAt());
        assertEquals("HTTP status 500", saved.getReason());

        ArgumentCaptor<AlertEvent> eventCaptor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertDispatchService).dispatch(eventCaptor.capture());
        assertEquals(AlertEventType.INCIDENT_OPENED, eventCaptor.getValue().type());
        assertEquals(monitorId, eventCaptor.getValue().monitorId());
    }

    @Test
    void resolvesOpenIncidentWhenRecovered() {
        UUID monitorId = UUID.randomUUID();
        Instant checkedAt = Instant.now();
        IncidentEntity existing = new IncidentEntity();
        existing.setId(UUID.randomUUID());
        existing.setMonitorId(monitorId);
        existing.setState(IncidentState.OPEN);
        existing.setOpenedAt(checkedAt.minusSeconds(30));
        existing.setReason("timeout");

        when(maintenanceWindowService.evaluate(monitorId, checkedAt)).thenReturn(MaintenanceEvaluation.inactive());
        when(incidentRepository.findTopByMonitorIdAndStateInOrderByOpenedAtDesc(monitorId, EnumSet.of(IncidentState.OPEN, IncidentState.ACKNOWLEDGED)))
                .thenReturn(Optional.of(existing));
        when(incidentRepository.save(existing)).thenReturn(existing);

        incidentService.applyTransition(monitorId, CheckStatus.UP, checkedAt, null);

        verify(incidentRepository).save(existing);
        assertEquals(IncidentState.RESOLVED, existing.getState());
        assertEquals(checkedAt, existing.getResolvedAt());

        ArgumentCaptor<AlertEvent> eventCaptor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertDispatchService).dispatch(eventCaptor.capture());
        assertEquals(AlertEventType.INCIDENT_RESOLVED, eventCaptor.getValue().type());
    }

    @Test
    void resolvesAcknowledgedIncidentWhenRecovered() {
        UUID monitorId = UUID.randomUUID();
        Instant checkedAt = Instant.now();
        IncidentEntity existing = new IncidentEntity();
        existing.setId(UUID.randomUUID());
        existing.setMonitorId(monitorId);
        existing.setState(IncidentState.ACKNOWLEDGED);
        existing.setOpenedAt(checkedAt.minusSeconds(30));
        existing.setReason("timeout");

        when(maintenanceWindowService.evaluate(monitorId, checkedAt)).thenReturn(MaintenanceEvaluation.inactive());
        when(incidentRepository.findTopByMonitorIdAndStateInOrderByOpenedAtDesc(monitorId, EnumSet.of(IncidentState.OPEN, IncidentState.ACKNOWLEDGED)))
                .thenReturn(Optional.of(existing));
        when(incidentRepository.save(existing)).thenReturn(existing);

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

        Instant checkedAt = Instant.now();
        when(maintenanceWindowService.evaluate(monitorId, checkedAt)).thenReturn(MaintenanceEvaluation.inactive());
        when(incidentRepository.findTopByMonitorIdAndStateInOrderByOpenedAtDesc(monitorId, EnumSet.of(IncidentState.OPEN, IncidentState.ACKNOWLEDGED)))
                .thenReturn(Optional.of(existing));

        incidentService.applyTransition(monitorId, CheckStatus.DOWN, checkedAt, "still down");

        verify(incidentRepository, never()).save(any());
        verify(alertDispatchService, never()).dispatch(any());
    }

    @Test
    void suppressPolicySkipsIncidentCreation() {
        UUID monitorId = UUID.randomUUID();
        Instant checkedAt = Instant.now();
        when(maintenanceWindowService.evaluate(monitorId, checkedAt)).thenReturn(
                new MaintenanceEvaluation(true, MaintenancePolicy.SUPPRESS, UUID.randomUUID(), "patch", "Maintenance active"));
        when(incidentRepository.findTopByMonitorIdAndStateInOrderByOpenedAtDesc(monitorId, EnumSet.of(IncidentState.OPEN, IncidentState.ACKNOWLEDGED)))
                .thenReturn(Optional.empty());

        incidentService.applyTransition(monitorId, CheckStatus.DOWN, checkedAt, "HTTP 503");

        verify(incidentRepository, never()).save(any());
        verify(alertDispatchService, never()).dispatch(any());
    }

    @Test
    void annotatePolicyAddsWindowContextToIncidentReason() {
        UUID monitorId = UUID.randomUUID();
        Instant checkedAt = Instant.now();
        when(maintenanceWindowService.evaluate(monitorId, checkedAt)).thenReturn(
                new MaintenanceEvaluation(true, MaintenancePolicy.ANNOTATE, UUID.randomUUID(), "deploy", "Maintenance window active: deploy (ANNOTATE)"));
        when(incidentRepository.findTopByMonitorIdAndStateInOrderByOpenedAtDesc(monitorId, EnumSet.of(IncidentState.OPEN, IncidentState.ACKNOWLEDGED)))
                .thenReturn(Optional.empty());
        when(incidentRepository.save(any(IncidentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        incidentService.applyTransition(monitorId, CheckStatus.DOWN, checkedAt, "HTTP 503");

        ArgumentCaptor<IncidentEntity> captor = ArgumentCaptor.forClass(IncidentEntity.class);
        verify(incidentRepository).save(captor.capture());
        assertEquals("HTTP 503 | Maintenance window active: deploy (ANNOTATE)", captor.getValue().getReason());
    }
}
