package io.openpulsechecker.service;

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

    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public void applyTransition(UUID monitorId, CheckStatus checkStatus, Instant checkedAt, String reason) {
        var openIncident = incidentRepository.findTopByMonitorIdAndStateOrderByOpenedAtDesc(monitorId, IncidentState.OPEN);

        if (checkStatus == CheckStatus.DOWN && openIncident.isEmpty()) {
            IncidentEntity incident = new IncidentEntity();
            incident.setMonitorId(monitorId);
            incident.setState(IncidentState.OPEN);
            incident.setOpenedAt(checkedAt);
            incident.setReason(reason == null || reason.isBlank() ? "Monitor reported DOWN" : reason);
            incidentRepository.save(incident);
            return;
        }

        if (checkStatus == CheckStatus.UP && openIncident.isPresent()) {
            IncidentEntity incident = openIncident.get();
            incident.setState(IncidentState.RESOLVED);
            incident.setResolvedAt(checkedAt);
            incidentRepository.save(incident);
        }
    }
}
