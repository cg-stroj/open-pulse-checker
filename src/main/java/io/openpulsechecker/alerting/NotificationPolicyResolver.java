package io.openpulsechecker.alerting;

import io.openpulsechecker.notificationpolicy.NotificationChannel;
import io.openpulsechecker.notificationpolicy.NotificationChannelScope;
import io.openpulsechecker.notificationpolicy.NotificationPolicyModel;
import io.openpulsechecker.notificationpolicy.NotificationPolicyRepository;
import io.openpulsechecker.notificationpolicy.NotificationPolicyScopeType;
import io.openpulsechecker.notificationpolicy.NotificationPolicyService;
import io.openpulsechecker.notificationpolicy.NotificationSeverity;
import io.openpulsechecker.persistence.MonitorRepository;
import io.openpulsechecker.persistence.StatusPageMonitorRepository;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationPolicyResolver {

    private final NotificationPolicyRepository policyRepository;
    private final NotificationPolicyService policyService;
    private final StatusPageMonitorRepository statusPageMonitorRepository;
    private final MonitorRepository monitorRepository;

    public NotificationPolicyResolver(NotificationPolicyRepository policyRepository,
                                      NotificationPolicyService policyService,
                                      StatusPageMonitorRepository statusPageMonitorRepository,
                                      MonitorRepository monitorRepository) {
        this.policyRepository = policyRepository;
        this.policyService = policyService;
        this.statusPageMonitorRepository = statusPageMonitorRepository;
        this.monitorRepository = monitorRepository;
    }

    @Transactional(readOnly = true)
    public Optional<NotificationDispatchPlan> resolve(AlertEvent event) {
        NotificationSeverity severity = mapSeverity(event.type());
        NotificationPolicyModel policy = resolvePolicyForMonitor(event.monitorId()).orElse(null);

        if (policy == null) {
            return Optional.of(new NotificationDispatchPlan(null, severity, 0, 0, NotificationChannelScope.activeChannels(), null));
        }
        if (!policy.enabled()) {
            return Optional.empty();
        }

        NotificationPolicyModel.RouteRule route = policy.routes().stream()
                .filter(r -> r.severity() == severity)
                .findFirst()
                .orElse(new NotificationPolicyModel.RouteRule(severity, NotificationChannelScope.activeChannels()));

        Set<NotificationChannel> channels = EnumSet.copyOf(route.channels());

        for (NotificationPolicyModel.EscalationStep step : policy.escalationSteps()) {
            if (step.afterSeconds() == 0 && severity.ordinal() <= step.minSeverity().ordinal()) {
                channels.addAll(step.channels());
            }
        }

        channels = NotificationChannelScope.filterToActive(channels);
        if (channels.isEmpty()) {
            channels = NotificationChannelScope.activeChannels();
        }

        if (channels.contains(NotificationChannel.EMAIL) && !isEmailAllowedForEvent(event)) {
            channels.remove(NotificationChannel.EMAIL);
        }

        return Optional.of(new NotificationDispatchPlan(policy.id(), severity, policy.cooldownSeconds(), policy.dedupSeconds(), channels, policy));
    }

    private boolean isEmailAllowedForEvent(AlertEvent event) {
        return monitorRepository.findById(event.monitorId())
                .map(monitor -> switch (event.type()) {
                    case INCIDENT_OPENED -> monitor.isEmailAlertOnDown();
                    case INCIDENT_RESOLVED -> monitor.isEmailAlertOnRecovery();
                })
                .orElse(true);
    }

    private Optional<NotificationPolicyModel> resolvePolicyForMonitor(UUID monitorId) {
        Optional<NotificationPolicyModel> monitorPolicy = policyRepository
                .findByScopeTypeAndScopeRefId(NotificationPolicyScopeType.MONITOR, monitorId)
                .map(policyService::toModel);
        if (monitorPolicy.isPresent()) {
            return monitorPolicy;
        }

        return statusPageMonitorRepository.findByMonitorId(monitorId).stream()
                .map(spm -> spm.getStatusPageId())
                .sorted(Comparator.naturalOrder())
                .map(statusPageId -> policyRepository.findByScopeTypeAndScopeRefId(NotificationPolicyScopeType.STATUS_PAGE, statusPageId))
                .flatMap(Optional::stream)
                .findFirst()
                .map(policyService::toModel)
                .or(() -> policyRepository.findByScopeType(NotificationPolicyScopeType.GLOBAL).map(policyService::toModel));
    }

    private NotificationSeverity mapSeverity(AlertEventType type) {
        return switch (type) {
            case INCIDENT_OPENED -> NotificationSeverity.CRITICAL;
            case INCIDENT_RESOLVED -> NotificationSeverity.INFO;
        };
    }
}
