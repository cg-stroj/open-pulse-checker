package io.openpulsechecker.incident;

import io.openpulsechecker.audit.AuditService;
import io.openpulsechecker.domain.IncidentState;
import io.openpulsechecker.persistence.IncidentEntity;
import io.openpulsechecker.persistence.IncidentRepository;
import io.openpulsechecker.service.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentAdminService {

    private final IncidentRepository incidentRepository;
    private final IncidentManualEventRepository incidentManualEventRepository;
    private final AuditService auditService;
    private final Clock clock;

    public IncidentAdminService(IncidentRepository incidentRepository,
                                IncidentManualEventRepository incidentManualEventRepository,
                                AuditService auditService,
                                Clock clock) {
        this.incidentRepository = incidentRepository;
        this.incidentManualEventRepository = incidentManualEventRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public IncidentEntity acknowledge(UUID incidentId, String reason) {
        IncidentEntity incident = requireIncident(incidentId);
        requireReason(reason);
        if (incident.getState() != IncidentState.OPEN) {
            throw new IllegalArgumentException("Only OPEN incidents can be acknowledged");
        }
        IncidentState from = incident.getState();
        incident.setState(IncidentState.ACKNOWLEDGED);
        IncidentEntity saved = incidentRepository.save(incident);
        persistManualEvent(saved.getId(), IncidentManualAction.ACKNOWLEDGED, from, saved.getState(), reason);
        auditService.log("incident.acknowledge", "incident:" + incidentId, "SUCCESS", reason);
        return saved;
    }

    @Transactional
    public IncidentEntity addAnnotation(UUID incidentId, String reason) {
        IncidentEntity incident = requireIncident(incidentId);
        requireReason(reason);
        persistManualEvent(incident.getId(), IncidentManualAction.ANNOTATION_ADDED, incident.getState(), incident.getState(), reason);
        auditService.log("incident.annotate", "incident:" + incidentId, "SUCCESS", reason);
        return incident;
    }

    @Transactional
    public IncidentEntity resolve(UUID incidentId, String reason) {
        IncidentEntity incident = requireIncident(incidentId);
        requireReason(reason);
        if (incident.getState() != IncidentState.OPEN && incident.getState() != IncidentState.ACKNOWLEDGED) {
            throw new IllegalArgumentException("Only OPEN or ACKNOWLEDGED incidents can be resolved manually");
        }
        IncidentState from = incident.getState();
        incident.setState(IncidentState.RESOLVED);
        incident.setResolvedAt(Instant.now(clock));
        IncidentEntity saved = incidentRepository.save(incident);
        persistManualEvent(saved.getId(), IncidentManualAction.RESOLVED_MANUALLY, from, saved.getState(), reason);
        auditService.log("incident.resolve.manual", "incident:" + incidentId, "SUCCESS", reason);
        return saved;
    }

    @Transactional
    public IncidentEntity reopen(UUID incidentId, String reason) {
        IncidentEntity incident = requireIncident(incidentId);
        requireReason(reason);
        if (incident.getState() != IncidentState.RESOLVED) {
            throw new IllegalArgumentException("Only RESOLVED incidents can be reopened");
        }
        IncidentState from = incident.getState();
        incident.setState(IncidentState.OPEN);
        incident.setResolvedAt(null);
        IncidentEntity saved = incidentRepository.save(incident);
        persistManualEvent(saved.getId(), IncidentManualAction.REOPENED, from, saved.getState(), reason);
        auditService.log("incident.reopen", "incident:" + incidentId, "SUCCESS", reason);
        return saved;
    }

    private IncidentEntity requireIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }

    private void persistManualEvent(UUID incidentId,
                                    IncidentManualAction action,
                                    IncidentState fromState,
                                    IncidentState toState,
                                    String reason) {
        IncidentManualEventEntity event = new IncidentManualEventEntity();
        event.setIncidentId(incidentId);
        event.setAction(action);
        event.setActor(currentUsername());
        event.setReason(reason.trim());
        event.setFromState(fromState);
        event.setToState(toState);
        event.setOccurredAt(Instant.now(clock));
        incidentManualEventRepository.save(event);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "system";
        }
        return auth.getName();
    }
}
