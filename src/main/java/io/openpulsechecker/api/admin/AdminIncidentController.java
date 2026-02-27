package io.openpulsechecker.api.admin;

import io.openpulsechecker.api.PagedResponse;
import io.openpulsechecker.domain.IncidentState;
import io.openpulsechecker.incident.AdminIncidentQueryService;
import io.openpulsechecker.incident.IncidentAdminService;
import io.openpulsechecker.persistence.IncidentEntity;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/incidents")
public class AdminIncidentController {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("openedAt", "resolvedAt", "state", "reason");

    private final IncidentAdminService incidentAdminService;
    private final AdminIncidentQueryService adminIncidentQueryService;

    public AdminIncidentController(IncidentAdminService incidentAdminService,
                                   AdminIncidentQueryService adminIncidentQueryService) {
        this.incidentAdminService = incidentAdminService;
        this.adminIncidentQueryService = adminIncidentQueryService;
    }

    @GetMapping
    public Object list(
            @RequestParam(required = false, defaultValue = "false") boolean paged,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "openedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) IncidentState state,
            @RequestParam(required = false) UUID monitorId,
            @RequestParam(required = false) String q
    ) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size), resolveSort(sortBy, sortDir));
        Page<AdminIncidentListItemResponse> result = adminIncidentQueryService.listIncidentsPage(state, monitorId, q, pageable);
        if (!paged) {
            return result.getContent();
        }
        return new PagedResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.hasPrevious()
        );
    }

    @GetMapping("/{incidentId}/events")
    public List<AdminIncidentEventResponse> events(@PathVariable UUID incidentId) {
        return adminIncidentQueryService.listEvents(incidentId);
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

    private int normalizeSize(int requested) {
        if (requested <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "openedAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, safeSortBy);
    }
}
