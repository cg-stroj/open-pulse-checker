package io.openpulsechecker.api;

import io.openpulsechecker.service.StatusPageService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StatusPageController {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "updatedAt", "name", "slug");

    private final StatusPageService statusPageService;

    public StatusPageController(StatusPageService statusPageService) {
        this.statusPageService = statusPageService;
    }

    @GetMapping("/public/status-pages/{slug}")
    public PublicStatusPageResponse getPublicBySlug(@PathVariable String slug) {
        return statusPageService.getPublicBySlug(slug);
    }

    @PostMapping("/status-pages")
    public ResponseEntity<StatusPageResponse> create(@Valid @RequestBody CreateStatusPageRequest request) {
        StatusPageResponse response = statusPageService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/status-pages/" + response.id())).body(response);
    }

    @PutMapping("/status-pages/{id}")
    public StatusPageResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateStatusPageRequest request) {
        return statusPageService.update(id, request);
    }

    @GetMapping("/status-pages/{id}/config")
    public StatusPageV2ConfigResponse getV2Config(@PathVariable UUID id) {
        return statusPageService.getConfig(id);
    }

    @PutMapping("/status-pages/{id}/config")
    public StatusPageV2ConfigResponse upsertV2Config(@PathVariable UUID id, @Valid @RequestBody UpsertStatusPageV2ConfigRequest request) {
        return statusPageService.upsertConfig(id, request);
    }

    @GetMapping("/status-pages")
    public Object list(
            @RequestParam(required = false, defaultValue = "false") boolean paged,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean isPublic
    ) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size), resolveSort(sortBy, sortDir));
        Page<StatusPageResponse> result = statusPageService.listPage(q, isPublic, pageable);
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

    @PostMapping("/status-pages/{id}/monitors")
    public List<PublicStatusPageResponse.PublicMonitorSummary> attachMonitors(@PathVariable UUID id,
                                                                              @Valid @RequestBody AttachStatusPageMonitorsRequest request) {
        return statusPageService.attachMonitors(id, request.monitorIds());
    }

    @DeleteMapping("/status-pages/{id}/monitors/{monitorId}")
    public ResponseEntity<Void> deleteMonitor(@PathVariable UUID id, @PathVariable UUID monitorId) {
        statusPageService.removeMonitor(id, monitorId);
        return ResponseEntity.noContent().build();
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
