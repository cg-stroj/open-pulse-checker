package io.openpulsechecker.api;

import io.openpulsechecker.domain.MonitorType;
import io.openpulsechecker.service.CheckExecutionService;
import io.openpulsechecker.service.MonitorService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/monitors")
public class MonitorController {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "updatedAt", "name", "intervalSec", "timeoutMs");

    private final MonitorService monitorService;
    private final CheckExecutionService checkExecutionService;

    public MonitorController(MonitorService monitorService, CheckExecutionService checkExecutionService) {
        this.monitorService = monitorService;
        this.checkExecutionService = checkExecutionService;
    }

    @PostMapping
    public ResponseEntity<MonitorResponse> create(@Valid @RequestBody CreateMonitorRequest request) {
        MonitorResponse response = monitorService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/monitors/" + response.id())).body(response);
    }

    @GetMapping
    public Object list(
            @RequestParam(required = false, defaultValue = "false") boolean paged,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) MonitorType type
    ) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                resolveSort(sortBy, sortDir)
        );
        Page<MonitorResponse> result = monitorService.listPage(q, enabled, type, pageable);

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

    @GetMapping("/{id}")
    public MonitorResponse get(@PathVariable UUID id) {
        return monitorService.get(id);
    }

    @PutMapping("/{id}")
    public MonitorResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateMonitorRequest request) {
        return monitorService.update(id, request);
    }

    @PatchMapping("/{id}/enabled")
    public MonitorResponse updateEnabled(@PathVariable UUID id, @Valid @RequestBody UpdateEnabledRequest request) {
        return monitorService.updateEnabled(id, request.enabled());
    }

    @PostMapping("/{id}/run-check")
    public CheckResultResponse runCheck(@PathVariable UUID id) {
        return checkExecutionService.runCheck(id);
    }

    private int normalizeSize(int requested) {
        if (requested <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "updatedAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, safeSortBy);
    }
}
