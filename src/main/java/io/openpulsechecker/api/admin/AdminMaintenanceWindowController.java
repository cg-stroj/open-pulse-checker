package io.openpulsechecker.api.admin;

import io.openpulsechecker.maintenance.MaintenanceWindowModel;
import io.openpulsechecker.maintenance.MaintenanceWindowService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/maintenance-windows")
public class AdminMaintenanceWindowController {

    private final MaintenanceWindowService maintenanceWindowService;

    public AdminMaintenanceWindowController(MaintenanceWindowService maintenanceWindowService) {
        this.maintenanceWindowService = maintenanceWindowService;
    }

    @GetMapping
    public List<MaintenanceWindowResponse> list() {
        return maintenanceWindowService.list().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public MaintenanceWindowResponse get(@PathVariable UUID id) {
        return toResponse(maintenanceWindowService.get(id));
    }

    @PostMapping
    public ResponseEntity<MaintenanceWindowResponse> create(@Valid @RequestBody UpsertMaintenanceWindowRequest request) {
        MaintenanceWindowResponse response = toResponse(maintenanceWindowService.create(toModel(request)));
        return ResponseEntity.created(URI.create("/api/v1/admin/maintenance-windows/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public MaintenanceWindowResponse update(@PathVariable UUID id,
                                            @Valid @RequestBody UpsertMaintenanceWindowRequest request) {
        return toResponse(maintenanceWindowService.update(id, toModel(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        maintenanceWindowService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private MaintenanceWindowModel toModel(UpsertMaintenanceWindowRequest request) {
        return new MaintenanceWindowModel(
                null,
                request.name(),
                request.scopeType(),
                request.scopeRefId(),
                request.type(),
                request.policy(),
                Boolean.TRUE.equals(request.enabled()),
                request.startAt(),
                request.endAt(),
                request.timezone(),
                request.recurringDays() == null ? List.of() : request.recurringDays(),
                request.recurringStartTime(),
                request.recurringEndTime(),
                null,
                null
        );
    }

    private MaintenanceWindowResponse toResponse(MaintenanceWindowModel model) {
        return new MaintenanceWindowResponse(
                model.id(),
                model.name(),
                model.scopeType(),
                model.scopeRefId(),
                model.type(),
                model.policy(),
                model.enabled(),
                model.startAt(),
                model.endAt(),
                model.timezone(),
                model.recurringDays(),
                model.recurringStartTime(),
                model.recurringEndTime(),
                model.createdAt(),
                model.updatedAt()
        );
    }
}
