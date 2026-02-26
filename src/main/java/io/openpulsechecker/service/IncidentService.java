package io.openpulsechecker.service;

import io.openpulsechecker.alerting.AlertDispatchService;
import io.openpulsechecker.alerting.AlertEvent;
import io.openpulsechecker.alerting.AlertEventType;
import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.domain.IncidentState;
import io.openpulsechecker.persistence.IncidentEntity;
import io.openpulsechecker.persistence.IncidentRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final AlertDispatchService alertDispatchService;

    public IncidentService(IncidentRepository incidentRepository, AlertDispatchService alertDispatchService) {
        this.incidentRepository = incidentRepository;
        this.alertDispatchService = alertDispatchService;
    }

    public void applyTransition(UUID monitorId, CheckStatus checkStatus, Instant checkedAt, String reason) {
        var openIncident = incidentRepository.findTopByMonitorIdAndStateOrderByOpenedAtDesc(monitorId, IncidentState.OPEN);

        if (checkStatus == CheckStatus.DOWN && openIncident.isEmpty()) {
            IncidentEntity incident = new IncidentEntity();
            incident.setMonitorId(monitorId);
            incident.setState(IncidentState.OPEN);
            incident.setOpenedAt(checkedAt);
            incident.setReason(reason == null || reason.isBlank() ? "Monitor reported DOWN" : reason);
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
                    saved.getReason()
            ));
        }
    }
}
