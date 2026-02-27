package io.openpulsechecker.incident;

import io.openpulsechecker.api.admin.AdminIncidentEventResponse;
import io.openpulsechecker.api.admin.AdminIncidentListItemResponse;
import io.openpulsechecker.domain.IncidentState;
import io.openpulsechecker.persistence.IncidentEntity;
import io.openpulsechecker.persistence.IncidentRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
        return mapIncidentListItems(incidents);
    }

    public Page<AdminIncidentListItemResponse> listIncidentsPage(IncidentState state,
                                                                 UUID monitorId,
                                                                 String q,
                                                                 Pageable pageable) {
        Specification<IncidentEntity> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (state != null) {
                predicate = cb.and(predicate, cb.equal(root.get("state"), state));
            }
            if (monitorId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("monitorId"), monitorId));
            }
            String normalizedQ = normalizeQuery(q);
            if (normalizedQ != null) {
                String like = "%" + normalizedQ.toLowerCase() + "%";
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("reason")), like));
            }
            return predicate;
        };

        Page<IncidentEntity> incidents = incidentRepository.findAll(spec, pageable);
        List<AdminIncidentListItemResponse> items = mapIncidentListItems(incidents.getContent());
        return new PageImpl<>(items, pageable, incidents.getTotalElements());
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

    private List<AdminIncidentListItemResponse> mapIncidentListItems(List<IncidentEntity> incidents) {
        Set<UUID> monitorIds = incidents.stream().map(IncidentEntity::getMonitorId).collect(Collectors.toSet());
        Map<UUID, String> monitorNamesById = monitorRepository.findAllById(monitorIds)
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

    private String normalizeQuery(String q) {
        if (q == null) return null;
        String trimmed = q.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
