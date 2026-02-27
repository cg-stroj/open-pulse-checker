package io.openpulsechecker.api.admin;

import io.openpulsechecker.incident.IncidentAdminService;
import io.openpulsechecker.persistence.IncidentEntity;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/incidents")
public class AdminIncidentController {

    private final IncidentAdminService incidentAdminService;

    public AdminIncidentController(IncidentAdminService incidentAdminService) {
        this.incidentAdminService = incidentAdminService;
    }

    @PostMapping("/{incidentId}/acknowledge")
    public AdminIncidentResponse acknowledge(@PathVariable UUID incidentId,
                                             @Valid @RequestBody IncidentActionRequest request) {
        return toResponse(incidentAdminService.acknowledge(incidentId, request.reason()));
    }

    @PostMapping("/{incidentId}/annotations")
    public AdminIncidentResponse annotate(@PathVariable UUID incidentId,
                                          @Valid @RequestBody IncidentActionRequest request) {
        return toResponse(incidentAdminService.addAnnotation(incidentId, request.reason()));
    }

    @PostMapping("/{incidentId}/resolve")
    public AdminIncidentResponse resolve(@PathVariable UUID incidentId,
                                         @Valid @RequestBody IncidentActionRequest request) {
        return toResponse(incidentAdminService.resolve(incidentId, request.reason()));
    }

    @PostMapping("/{incidentId}/reopen")
    public AdminIncidentResponse reopen(@PathVariable UUID incidentId,
                                        @Valid @RequestBody IncidentActionRequest request) {
        return toResponse(incidentAdminService.reopen(incidentId, request.reason()));
    }

    private AdminIncidentResponse toResponse(IncidentEntity incident) {
        return new AdminIncidentResponse(
                incident.getId(),
                incident.getMonitorId(),
                incident.getState(),
                incident.getOpenedAt(),
                incident.getResolvedAt(),
                incident.getReason()
        );
    }
}
