package io.openpulsechecker.notificationpolicy;

import io.openpulsechecker.persistence.MonitorRepository;
import io.openpulsechecker.persistence.StatusPageRepository;
import io.openpulsechecker.service.ResourceNotFoundException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPolicyService {
    private final NotificationPolicyRepository policyRepository;
    private final NotificationRouteRuleRepository routeRuleRepository;
    private final NotificationEscalationStepRepository escalationStepRepository;
    private final MonitorRepository monitorRepository;
    private final StatusPageRepository statusPageRepository;

    public NotificationPolicyService(NotificationPolicyRepository policyRepository,
                                     NotificationRouteRuleRepository routeRuleRepository,
                                     NotificationEscalationStepRepository escalationStepRepository,
                                     MonitorRepository monitorRepository,
                                     StatusPageRepository statusPageRepository) {
        this.policyRepository = policyRepository;
        this.routeRuleRepository = routeRuleRepository;
        this.escalationStepRepository = escalationStepRepository;
        this.monitorRepository = monitorRepository;
        this.statusPageRepository = statusPageRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationPolicyModel> list() { return policyRepository.findAll().stream().map(this::toModel).toList(); }
    @Transactional(readOnly = true)
    public NotificationPolicyModel get(UUID id) { return toModel(requirePolicy(id)); }

    @Transactional
    public NotificationPolicyModel create(NotificationPolicyModel input) {
        validateInput(input);
        policyRepository.findByScopeTypeAndScopeRefId(input.scopeType(), input.scopeRefId())
                .ifPresent(existing -> { throw new IllegalArgumentException("Policy for scope already exists."); });
        NotificationPolicyEntity entity = new NotificationPolicyEntity();
        apply(entity, input);
        NotificationPolicyEntity saved = policyRepository.save(entity);
        replaceChildren(saved.getId(), input);
        return toModel(saved);
    }

    @Transactional
    public NotificationPolicyModel update(UUID id, NotificationPolicyModel input) {
        validateInput(input);
        NotificationPolicyEntity existing = requirePolicy(id);
        policyRepository.findByScopeTypeAndScopeRefId(input.scopeType(), input.scopeRefId())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new IllegalArgumentException("Policy for scope already exists."); });
        apply(existing, input);
        NotificationPolicyEntity saved = policyRepository.save(existing);
        replaceChildren(saved.getId(), input);
        return toModel(saved);
    }

    public NotificationPolicyModel toModel(NotificationPolicyEntity entity) {
        List<NotificationPolicyModel.RouteRule> routes = routeRuleRepository.findByPolicyId(entity.getId()).stream()
                .map(r -> new NotificationPolicyModel.RouteRule(r.getSeverity(), r.toChannels()))
                .sorted(Comparator.comparing(NotificationPolicyModel.RouteRule::severity))
                .toList();
        List<NotificationPolicyModel.EscalationStep> steps = escalationStepRepository.findByPolicyIdOrderByStepOrderAsc(entity.getId()).stream()
                .map(s -> new NotificationPolicyModel.EscalationStep(s.getStepOrder(), s.getAfterSeconds(), s.getMinSeverity(), s.toChannels()))
                .toList();
        return new NotificationPolicyModel(entity.getId(), entity.getScopeType(), entity.getScopeRefId(), entity.isEnabled(),
                entity.getCooldownSeconds(), entity.getDedupSeconds(), routes, steps, entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private void apply(NotificationPolicyEntity entity, NotificationPolicyModel input) {
        entity.setScopeType(input.scopeType());
        entity.setScopeRefId(input.scopeRefId());
        entity.setEnabled(input.enabled());
        entity.setCooldownSeconds(input.cooldownSeconds());
        entity.setDedupSeconds(input.dedupSeconds());
    }

    private void replaceChildren(UUID policyId, NotificationPolicyModel input) {
        routeRuleRepository.deleteByPolicyId(policyId);
        escalationStepRepository.deleteByPolicyId(policyId);
        for (NotificationPolicyModel.RouteRule route : input.routes()) {
            NotificationRouteRuleEntity entity = new NotificationRouteRuleEntity();
            entity.setPolicyId(policyId);
            entity.setSeverity(route.severity());
            entity.setChannels(route.channels());
            routeRuleRepository.save(entity);
        }
        for (NotificationPolicyModel.EscalationStep step : input.escalationSteps()) {
            NotificationEscalationStepEntity entity = new NotificationEscalationStepEntity();
            entity.setPolicyId(policyId);
            entity.setStepOrder(step.stepOrder());
            entity.setAfterSeconds(step.afterSeconds());
            entity.setMinSeverity(step.minSeverity());
            entity.setChannels(step.channels());
            escalationStepRepository.save(entity);
        }
    }

    private void validateInput(NotificationPolicyModel input) {
        if (input.cooldownSeconds() < 0 || input.dedupSeconds() < 0) throw new IllegalArgumentException("cooldownSeconds and dedupSeconds must be >= 0");
        if (input.scopeType() == NotificationPolicyScopeType.GLOBAL && input.scopeRefId() != null) throw new IllegalArgumentException("GLOBAL scope does not accept scopeRefId");
        if (input.scopeType() != NotificationPolicyScopeType.GLOBAL && input.scopeRefId() == null) throw new IllegalArgumentException("scopeRefId is required for non-global scopes");
        if (input.scopeType() == NotificationPolicyScopeType.MONITOR && !monitorRepository.existsById(input.scopeRefId())) throw new ResourceNotFoundException("Monitor not found: " + input.scopeRefId());
        if (input.scopeType() == NotificationPolicyScopeType.STATUS_PAGE && !statusPageRepository.existsById(input.scopeRefId())) throw new ResourceNotFoundException("Status page not found: " + input.scopeRefId());
        Set<NotificationSeverity> seenSeverities = EnumSet.noneOf(NotificationSeverity.class);
        for (NotificationPolicyModel.RouteRule route : input.routes()) {
            if (!seenSeverities.add(route.severity())) throw new IllegalArgumentException("Duplicate severity route: " + route.severity());
            if (route.channels() == null || route.channels().isEmpty()) throw new IllegalArgumentException("At least one channel required for route: " + route.severity());
        }
        if (seenSeverities.size() != NotificationSeverity.values().length) throw new IllegalArgumentException("Routes must include all severities exactly once");
        Set<Integer> orders = input.escalationSteps().stream().map(NotificationPolicyModel.EscalationStep::stepOrder).collect(Collectors.toSet());
        if (orders.size() != input.escalationSteps().size()) throw new IllegalArgumentException("Escalation step order must be unique");
        if (input.escalationSteps().stream().anyMatch(step -> step.channels() == null || step.channels().isEmpty())) {
            throw new IllegalArgumentException("At least one channel required per escalation step");
        }
    }

    private NotificationPolicyEntity requirePolicy(UUID id) {
        return policyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification policy not found: " + id));
    }
}
