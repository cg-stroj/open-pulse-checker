package io.openpulsechecker.api;

import io.openpulsechecker.service.StatusPageService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StatusPageController {

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

    @GetMapping("/status-pages")
    public List<StatusPageResponse> list() {
        return statusPageService.list();
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
}
