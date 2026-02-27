package io.openpulsechecker.service;

import io.openpulsechecker.api.CreateMonitorRequest;
import io.openpulsechecker.api.MonitorResponse;
import io.openpulsechecker.api.UpdateMonitorRequest;
import io.openpulsechecker.audit.AuditService;
import io.openpulsechecker.domain.MonitorType;
import io.openpulsechecker.persistence.CheckResultEntity;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import jakarta.persistence.criteria.Predicate;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final CheckResultRepository checkResultRepository;
    private final AuditService auditService;

    public MonitorService(MonitorRepository monitorRepository,
                          CheckResultRepository checkResultRepository,
                          AuditService auditService) {
        this.monitorRepository = monitorRepository;
        this.checkResultRepository = checkResultRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MonitorResponse create(CreateMonitorRequest request) {
        validateTargetUrl(request.targetUrl());

        MonitorEntity entity = new MonitorEntity();
        entity.setName(request.name().trim());
        entity.setType(request.type());
        entity.setTargetUrl(request.targetUrl().trim());
        entity.setIntervalSec(request.intervalSec());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setTimeoutMs(request.timeoutMs());

        MonitorEntity saved = monitorRepository.save(entity);
        auditService.log("MONITOR_CREATE", "monitor:" + saved.getId(), "SUCCESS", saved.getName());
        return toResponse(saved, null);
    }

    @Transactional(readOnly = true)
    public List<MonitorResponse> listAll() {
        List<MonitorEntity> monitors = monitorRepository.findAll();
        return mapResponses(monitors);
    }

    @Transactional(readOnly = true)
    public Page<MonitorResponse> listPage(String q, Boolean enabled, MonitorType type, Pageable pageable) {
        Specification<MonitorEntity> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (enabled != null) {
                predicate = cb.and(predicate, cb.equal(root.get("enabled"), enabled));
            }
            if (type != null) {
                predicate = cb.and(predicate, cb.equal(root.get("type"), type));
            }
            String normalizedQ = normalizeQuery(q);
            if (normalizedQ != null) {
                String like = "%" + normalizedQ.toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("targetUrl")), like)
                ));
            }
            return predicate;
        };

        Page<MonitorEntity> page = monitorRepository.findAll(spec, pageable);
        List<MonitorResponse> items = mapResponses(page.getContent());
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MonitorEntity getEntity(UUID id) {
        return monitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitor not found: " + id));
    }

    @Transactional(readOnly = true)
    public MonitorResponse get(UUID id) {
        MonitorEntity entity = getEntity(id);
        CheckResultEntity lastCheck = checkResultRepository.findTopByMonitorIdOrderByCheckedAtDesc(id).orElse(null);
        return toResponse(entity, lastCheck);
    }

    @Transactional
    public MonitorResponse update(UUID id, UpdateMonitorRequest request) {
        validateTargetUrl(request.targetUrl());

        MonitorEntity entity = getEntity(id);
        entity.setName(request.name().trim());
        entity.setType(request.type());
        entity.setTargetUrl(request.targetUrl().trim());
        entity.setIntervalSec(request.intervalSec());
        entity.setEnabled(request.enabled());
        entity.setTimeoutMs(request.timeoutMs());

        MonitorEntity saved = monitorRepository.save(entity);
        auditService.log("MONITOR_UPDATE", "monitor:" + saved.getId(), "SUCCESS", saved.getName());
        CheckResultEntity lastCheck = checkResultRepository.findTopByMonitorIdOrderByCheckedAtDesc(id).orElse(null);
        return toResponse(saved, lastCheck);
    }

    @Transactional
    public MonitorResponse updateEnabled(UUID id, boolean enabled) {
        MonitorEntity entity = getEntity(id);
        entity.setEnabled(enabled);
        MonitorEntity saved = monitorRepository.save(entity);
        auditService.log("MONITOR_UPDATE_ENABLED", "monitor:" + saved.getId(), "SUCCESS", "enabled=" + enabled);
        CheckResultEntity lastCheck = checkResultRepository.findTopByMonitorIdOrderByCheckedAtDesc(id).orElse(null);
        return toResponse(saved, lastCheck);
    }

    private void validateTargetUrl(String rawUrl) {
        URI uri = URI.create(rawUrl);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only HTTP/HTTPS target URLs are allowed.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Target URL host is required.");
        }
    }

    private List<MonitorResponse> mapResponses(List<MonitorEntity> monitors) {
        if (monitors.isEmpty()) return List.of();

        List<UUID> ids = monitors.stream().map(MonitorEntity::getId).toList();
        Map<UUID, CheckResultEntity> latestByMonitor = new HashMap<>();
        for (CheckResultEntity check : checkResultRepository.findLatestForMonitorIds(ids)) {
            latestByMonitor.put(check.getMonitorId(), check);
        }

        return monitors.stream().map(monitor -> toResponse(monitor, latestByMonitor.get(monitor.getId()))).toList();
    }

    private MonitorResponse toResponseWithLatestCheck(MonitorEntity entity) {
        CheckResultEntity latest = checkResultRepository.findTopByMonitorIdOrderByCheckedAtDesc(entity.getId()).orElse(null);
        return toResponse(entity, latest);
    }

    private String normalizeQuery(String q) {
        if (q == null) return null;
        String trimmed = q.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private MonitorResponse toResponse(MonitorEntity entity, CheckResultEntity latestCheck) {
        return new MonitorResponse(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getTargetUrl(),
                entity.getIntervalSec(),
                entity.isEnabled(),
                entity.getTimeoutMs(),
                latestCheck != null ? latestCheck.getCheckedAt() : null,
                latestCheck != null ? latestCheck.getStatus() : null,
                latestCheck != null ? latestCheck.getStatusCode() : null,
                latestCheck != null ? latestCheck.getLatencyMs() : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
