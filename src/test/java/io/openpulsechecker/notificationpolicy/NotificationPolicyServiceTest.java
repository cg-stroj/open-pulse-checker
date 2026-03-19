package io.openpulsechecker.notificationpolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.openpulsechecker.persistence.MonitorRepository;
import io.openpulsechecker.persistence.StatusPageRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationPolicyServiceTest {

    @Test
    void createRejectsNonEmailChannels() {
        NotificationPolicyRepository policyRepository = org.mockito.Mockito.mock(NotificationPolicyRepository.class);
        NotificationRouteRuleRepository routeRuleRepository = org.mockito.Mockito.mock(NotificationRouteRuleRepository.class);
        NotificationEscalationStepRepository escalationStepRepository = org.mockito.Mockito.mock(NotificationEscalationStepRepository.class);
        MonitorRepository monitorRepository = org.mockito.Mockito.mock(MonitorRepository.class);
        StatusPageRepository statusPageRepository = org.mockito.Mockito.mock(StatusPageRepository.class);

        NotificationPolicyService service = new NotificationPolicyService(
                policyRepository, routeRuleRepository, escalationStepRepository, monitorRepository, statusPageRepository);

        when(policyRepository.findByScopeTypeAndScopeRefId(NotificationPolicyScopeType.GLOBAL, null)).thenReturn(Optional.empty());

        NotificationPolicyModel model = new NotificationPolicyModel(
                null,
                NotificationPolicyScopeType.GLOBAL,
                null,
                true,
                0,
                0,
                List.of(
                        new NotificationPolicyModel.RouteRule(NotificationSeverity.CRITICAL, EnumSet.of(NotificationChannel.WEBHOOK)),
                        new NotificationPolicyModel.RouteRule(NotificationSeverity.HIGH, EnumSet.of(NotificationChannel.EMAIL)),
                        new NotificationPolicyModel.RouteRule(NotificationSeverity.MEDIUM, EnumSet.of(NotificationChannel.EMAIL)),
                        new NotificationPolicyModel.RouteRule(NotificationSeverity.LOW, EnumSet.of(NotificationChannel.EMAIL)),
                        new NotificationPolicyModel.RouteRule(NotificationSeverity.INFO, EnumSet.of(NotificationChannel.EMAIL))
                ),
                List.of(),
                Instant.now(),
                Instant.now());

        assertThrows(IllegalArgumentException.class, () -> service.create(model));
    }

    @Test
    void toModelFiltersLegacyChannelsToEmailOnly() {
        NotificationPolicyRepository policyRepository = org.mockito.Mockito.mock(NotificationPolicyRepository.class);
        NotificationRouteRuleRepository routeRuleRepository = org.mockito.Mockito.mock(NotificationRouteRuleRepository.class);
        NotificationEscalationStepRepository escalationStepRepository = org.mockito.Mockito.mock(NotificationEscalationStepRepository.class);
        MonitorRepository monitorRepository = org.mockito.Mockito.mock(MonitorRepository.class);
        StatusPageRepository statusPageRepository = org.mockito.Mockito.mock(StatusPageRepository.class);

        NotificationPolicyService service = new NotificationPolicyService(
                policyRepository, routeRuleRepository, escalationStepRepository, monitorRepository, statusPageRepository);

        NotificationPolicyEntity entity = new NotificationPolicyEntity();
        entity.setId(UUID.randomUUID());
        entity.setScopeType(NotificationPolicyScopeType.GLOBAL);
        entity.setScopeRefId(null);
        entity.setEnabled(true);
        entity.setCooldownSeconds(0);
        entity.setDedupSeconds(0);

        NotificationRouteRuleEntity route = new NotificationRouteRuleEntity();
        route.setPolicyId(entity.getId());
        route.setSeverity(NotificationSeverity.CRITICAL);
        route.setChannels(EnumSet.of(NotificationChannel.WEBHOOK, NotificationChannel.SLACK));

        when(routeRuleRepository.findByPolicyId(entity.getId())).thenReturn(List.of(route));
        when(escalationStepRepository.findByPolicyIdOrderByStepOrderAsc(entity.getId())).thenReturn(List.of());

        NotificationPolicyModel model = service.toModel(entity);
        assertEquals(EnumSet.of(NotificationChannel.EMAIL), model.routes().get(0).channels());
    }
}
