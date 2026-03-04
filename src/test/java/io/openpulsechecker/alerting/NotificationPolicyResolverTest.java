package io.openpulsechecker.alerting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.openpulsechecker.notificationpolicy.NotificationPolicyEntity;
import io.openpulsechecker.notificationpolicy.NotificationPolicyModel;
import io.openpulsechecker.notificationpolicy.NotificationPolicyRepository;
import io.openpulsechecker.notificationpolicy.NotificationPolicyScopeType;
import io.openpulsechecker.notificationpolicy.NotificationPolicyService;
import io.openpulsechecker.notificationpolicy.NotificationSeverity;
import io.openpulsechecker.persistence.StatusPageMonitorEntity;
import io.openpulsechecker.persistence.StatusPageMonitorRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationPolicyResolverTest {

    @Test
    void monitorPolicyOverridesStatusPageAndGlobal() {
        NotificationPolicyRepository policyRepository = org.mockito.Mockito.mock(NotificationPolicyRepository.class);
        NotificationPolicyService policyService = org.mockito.Mockito.mock(NotificationPolicyService.class);
        StatusPageMonitorRepository statusPageMonitorRepository = org.mockito.Mockito.mock(StatusPageMonitorRepository.class);

        NotificationPolicyResolver resolver = new NotificationPolicyResolver(policyRepository, policyService, statusPageMonitorRepository);

        UUID monitorId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();

        StatusPageMonitorEntity link = new StatusPageMonitorEntity();
        link.setMonitorId(monitorId);
        link.setStatusPageId(pageId);

        NotificationPolicyEntity monitorPolicyEntity = new NotificationPolicyEntity();
        monitorPolicyEntity.setId(UUID.randomUUID());
        NotificationPolicyModel monitorPolicy = model(monitorPolicyEntity.getId());

        when(policyRepository.findByScopeTypeAndScopeRefId(NotificationPolicyScopeType.MONITOR, monitorId))
                .thenReturn(Optional.of(monitorPolicyEntity));
        when(policyService.toModel(monitorPolicyEntity)).thenReturn(monitorPolicy);

        NotificationDispatchPlan plan = resolver.resolve(new AlertEvent(
                AlertEventType.INCIDENT_OPENED, monitorId, UUID.randomUUID(), Instant.now(), "down")).orElseThrow();

        assertEquals(monitorPolicy.id(), plan.policyId());
        assertTrue(plan.channels().contains(io.openpulsechecker.notificationpolicy.NotificationChannel.WEBHOOK));
    }

    private NotificationPolicyModel model(UUID id) {
        return new NotificationPolicyModel(id, NotificationPolicyScopeType.MONITOR, UUID.randomUUID(), true, 0, 0,
                List.of(
                        new NotificationPolicyModel.RouteRule(NotificationSeverity.CRITICAL, EnumSet.of(io.openpulsechecker.notificationpolicy.NotificationChannel.WEBHOOK)),
                        new NotificationPolicyModel.RouteRule(NotificationSeverity.HIGH, EnumSet.of(io.openpulsechecker.notificationpolicy.NotificationChannel.WEBHOOK)),
                        new NotificationPolicyModel.RouteRule(NotificationSeverity.MEDIUM, EnumSet.of(io.openpulsechecker.notificationpolicy.NotificationChannel.WEBHOOK)),
                        new NotificationPolicyModel.RouteRule(NotificationSeverity.LOW, EnumSet.of(io.openpulsechecker.notificationpolicy.NotificationChannel.WEBHOOK)),
                        new NotificationPolicyModel.RouteRule(NotificationSeverity.INFO, EnumSet.of(io.openpulsechecker.notificationpolicy.NotificationChannel.WEBHOOK))
                ),
                List.of(), Instant.now(), Instant.now());
    }
}
