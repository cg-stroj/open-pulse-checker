package io.openpulsechecker.api;

import io.openpulsechecker.service.CheckExecutionService;
import io.openpulsechecker.service.MonitorService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/monitors")
public class MonitorController {

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
    public List<MonitorResponse> list() {
        return monitorService.listAll();
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
}
