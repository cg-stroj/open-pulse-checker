package io.openpulsechecker.api.admin;

import io.openpulsechecker.alerting.AlertDispatchService;
import io.openpulsechecker.notificationpolicy.NotificationChannelScope;
import io.openpulsechecker.notificationpolicy.NotificationPolicyModel;
import io.openpulsechecker.notificationpolicy.NotificationPolicyService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final AlertDispatchService alertDispatchService;

    public AdminNotificationPolicyController(NotificationPolicyService notificationPolicyService,
                                             AlertDispatchService alertDispatchService) {
        this.notificationPolicyService = notificationPolicyService;
        this.alertDispatchService = alertDispatchService;
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

    @PostMapping("/{id}/test")
    public Map<String, String> test(@PathVariable UUID id,
                                    @RequestBody(required = false) NotificationPolicyTestTriggerRequest request) {
        NotificationPolicyModel model = notificationPolicyService.get(id);
        Set<io.openpulsechecker.notificationpolicy.NotificationChannel> channels = request != null && request.channels() != null && !request.channels().isEmpty()
                ? NotificationChannelScope.filterToActive(request.channels())
                : model.routes().stream().flatMap(r -> r.channels().stream())
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(io.openpulsechecker.notificationpolicy.NotificationChannel.class)));
        channels = NotificationChannelScope.filterToActive(channels);
        if (channels.isEmpty()) {
            channels = NotificationChannelScope.activeChannels();
        }
        alertDispatchService.dispatchTest(id, channels, request == null ? "policy-test" : request.reason());
        return Map.of("status", "triggered", "policyId", id.toString(), "channels", channels.toString());
    }

    private NotificationPolicyModel toModel(UpsertNotificationPolicyRequest request) {
        List<NotificationPolicyModel.EscalationStep> escalationSteps = request.escalationSteps() == null
                ? List.of()
                : request.escalationSteps().stream()
                .map(s -> new NotificationPolicyModel.EscalationStep(
                        s.stepOrder(), s.afterSeconds(), s.minSeverity(), s.channels()))
                .toList();
        return new NotificationPolicyModel(
                null,
                request.scopeType(),
                request.scopeRefId(),
                Boolean.TRUE.equals(request.enabled()),
                request.cooldownSeconds(),
                request.dedupSeconds(),
                request.routes().stream().map(r -> new NotificationPolicyModel.RouteRule(r.severity(), r.channels())).toList(),
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
                model.routes().stream().map(r -> new NotificationPolicyResponse.RouteResponse(r.severity(), r.channels())).toList(),
                model.escalationSteps().stream().map(s -> new NotificationPolicyResponse.EscalationStepResponse(
                        s.stepOrder(), s.afterSeconds(), s.minSeverity(), s.channels())).toList(),
                model.createdAt(),
                model.updatedAt()
        );
    }
}
