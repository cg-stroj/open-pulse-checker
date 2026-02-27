package io.openpulsechecker.api.admin;

import io.openpulsechecker.notificationpolicy.NotificationPolicyModel;
import io.openpulsechecker.notificationpolicy.NotificationPolicyService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notification-policies")
public class AdminNotificationPolicyController {

    private final NotificationPolicyService notificationPolicyService;

    public AdminNotificationPolicyController(NotificationPolicyService notificationPolicyService) {
        this.notificationPolicyService = notificationPolicyService;
    }

    @GetMapping
    public List<NotificationPolicyResponse> list() {
        return notificationPolicyService.list().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public NotificationPolicyResponse get(@PathVariable UUID id) {
        return toResponse(notificationPolicyService.get(id));
    }

    @PostMapping
    public ResponseEntity<NotificationPolicyResponse> create(@Valid @RequestBody UpsertNotificationPolicyRequest request) {
        NotificationPolicyResponse response = toResponse(notificationPolicyService.create(toModel(request)));
        return ResponseEntity.created(URI.create("/api/v1/admin/notification-policies/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public NotificationPolicyResponse update(@PathVariable UUID id,
                                             @Valid @RequestBody UpsertNotificationPolicyRequest request) {
        return toResponse(notificationPolicyService.update(id, toModel(request)));
    }

    private NotificationPolicyModel toModel(UpsertNotificationPolicyRequest request) {
        List<NotificationPolicyModel.EscalationStep> escalationSteps = request.escalationSteps() == null
                ? List.of()
                : request.escalationSteps().stream()
                .map(s -> new NotificationPolicyModel.EscalationStep(
                        s.stepOrder(), s.afterSeconds(), s.minSeverity(), Boolean.TRUE.equals(s.webhookEnabled())))
                .toList();
        return new NotificationPolicyModel(
                null,
                request.scopeType(),
                request.scopeRefId(),
                Boolean.TRUE.equals(request.enabled()),
                request.cooldownSeconds(),
                request.dedupSeconds(),
                request.routes().stream().map(r -> new NotificationPolicyModel.RouteRule(r.severity(), Boolean.TRUE.equals(r.webhookEnabled()))).toList(),
                escalationSteps,
                null,
                null
        );
    }

    private NotificationPolicyResponse toResponse(NotificationPolicyModel model) {
        return new NotificationPolicyResponse(
                model.id(),
                model.scopeType(),
                model.scopeRefId(),
                model.enabled(),
                model.cooldownSeconds(),
                model.dedupSeconds(),
                model.routes().stream().map(r -> new NotificationPolicyResponse.RouteResponse(r.severity(), r.webhookEnabled())).toList(),
                model.escalationSteps().stream().map(s -> new NotificationPolicyResponse.EscalationStepResponse(
                        s.stepOrder(), s.afterSeconds(), s.minSeverity(), s.webhookEnabled())).toList(),
                model.createdAt(),
                model.updatedAt()
        );
    }
}
