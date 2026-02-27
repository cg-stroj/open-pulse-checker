package io.openpulsechecker.incident;

import io.openpulsechecker.api.admin.AdminIncidentEventResponse;
import io.openpulsechecker.api.admin.AdminIncidentListItemResponse;
import io.openpulsechecker.persistence.IncidentEntity;
import io.openpulsechecker.persistence.IncidentRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AdminIncidentQueryService {

    private final IncidentRepository incidentRepository;
    private final MonitorRepository monitorRepository;
    private final IncidentManualEventRepository incidentManualEventRepository;

    public AdminIncidentQueryService(IncidentRepository incidentRepository,
                                     MonitorRepository monitorRepository,
                                     IncidentManualEventRepository incidentManualEventRepository) {
        this.incidentRepository = incidentRepository;
        this.monitorRepository = monitorRepository;
        this.incidentManualEventRepository = incidentManualEventRepository;
    }

    public List<AdminIncidentListItemResponse> listIncidents() {
        List<IncidentEntity> incidents = incidentRepository.findAllByOrderByOpenedAtDesc();
        Map<UUID, String> monitorNamesById = monitorRepository.findAllById(
                        incidents.stream().map(IncidentEntity::getMonitorId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(MonitorEntity::getId, MonitorEntity::getName, (first, second) -> first));

        return incidents.stream()
                .map(incident -> new AdminIncidentListItemResponse(
                        incident.getId(),
                        incident.getMonitorId(),
                        monitorNamesById.getOrDefault(incident.getMonitorId(), "Unknown monitor"),
                        incident.getState(),
                        incident.getOpenedAt(),
                        incident.getResolvedAt(),
                        incident.getReason()))
                .toList();
    }

    public List<AdminIncidentEventResponse> listEvents(UUID incidentId) {
        return incidentManualEventRepository.findByIncidentIdOrderByOccurredAtAsc(incidentId)
                .stream()
                .map(event -> new AdminIncidentEventResponse(
                        event.getId(),
                        event.getAction(),
                        event.getActor(),
                        event.getReason(),
                        event.getFromState(),
                        event.getToState(),
                        event.getOccurredAt()))
                .toList();
    }
}
