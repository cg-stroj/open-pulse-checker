package io.openpulsechecker.service;

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
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final AlertDispatchService alertDispatchService;
    private final MaintenanceWindowService maintenanceWindowService;

    public IncidentService(IncidentRepository incidentRepository,
                           AlertDispatchService alertDispatchService,
                           MaintenanceWindowService maintenanceWindowService) {
        this.incidentRepository = incidentRepository;
        this.alertDispatchService = alertDispatchService;
        this.maintenanceWindowService = maintenanceWindowService;
    }

    public void applyTransition(UUID monitorId, CheckStatus checkStatus, Instant checkedAt, String reason) {
        MaintenanceEvaluation maintenance = maintenanceWindowService.evaluate(monitorId, checkedAt);
        var openIncident = incidentRepository.findTopByMonitorIdAndStateOrderByOpenedAtDesc(monitorId, IncidentState.OPEN);

        if (checkStatus == CheckStatus.DOWN && openIncident.isEmpty()) {
            if (maintenance.active() && maintenance.policy() == MaintenancePolicy.SUPPRESS) {
                return;
            }
            IncidentEntity incident = new IncidentEntity();
            incident.setMonitorId(monitorId);
            incident.setState(IncidentState.OPEN);
            incident.setOpenedAt(checkedAt);
            incident.setReason(annotateReason(reason == null || reason.isBlank() ? "Monitor reported DOWN" : reason, maintenance));
            IncidentEntity saved = incidentRepository.save(incident);
            alertDispatchService.dispatch(new AlertEvent(
                    AlertEventType.INCIDENT_OPENED,
                    monitorId,
                    saved.getId(),
                    checkedAt,
                    saved.getReason()
            ));
            return;
        }

        if (checkStatus == CheckStatus.UP && openIncident.isPresent()) {
            IncidentEntity incident = openIncident.get();
            incident.setState(IncidentState.RESOLVED);
            incident.setResolvedAt(checkedAt);
            IncidentEntity saved = incidentRepository.save(incident);
            alertDispatchService.dispatch(new AlertEvent(
                    AlertEventType.INCIDENT_RESOLVED,
                    monitorId,
                    saved.getId(),
                    checkedAt,
                    annotateReason(saved.getReason(), maintenance)
            ));
        }
    }

    private String annotateReason(String baseReason, MaintenanceEvaluation maintenance) {
        if (!maintenance.active() || maintenance.policy() != MaintenancePolicy.ANNOTATE) {
            return baseReason;
        }
        return baseReason + " | " + maintenance.annotation();
    }
}
