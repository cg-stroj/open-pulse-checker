package io.openpulsechecker.service;

import io.openpulsechecker.api.CreateMonitorRequest;
import io.openpulsechecker.api.MonitorResponse;
import io.openpulsechecker.api.UpdateMonitorRequest;
import io.openpulsechecker.audit.AuditService;
import io.openpulsechecker.domain.MonitorType;
import io.openpulsechecker.persistence.CheckResultEntity;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.IncidentRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import io.openpulsechecker.persistence.StatusPageMonitorRepository;
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
    private final IncidentRepository incidentRepository;
    private final StatusPageMonitorRepository statusPageMonitorRepository;
    private final AuditService auditService;

    public MonitorService(MonitorRepository monitorRepository,
                          CheckResultRepository checkResultRepository,
                          IncidentRepository incidentRepository,
                          StatusPageMonitorRepository statusPageMonitorRepository,
                          AuditService auditService) {
        this.monitorRepository = monitorRepository;
        this.checkResultRepository = checkResultRepository;
        this.incidentRepository = incidentRepository;
        this.statusPageMonitorRepository = statusPageMonitorRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MonitorResponse create(CreateMonitorRequest request) {
        validateTargetUrl(request.targetUrl(), request.type());

        MonitorEntity entity = new MonitorEntity();
        entity.setName(request.name().trim());
        entity.setType(request.type());
        entity.setTargetUrl(request.targetUrl().trim());
        entity.setIntervalSec(request.intervalSec());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setTimeoutMs(request.timeoutMs());
        
        if (request.type() == MonitorType.HTTP) {
            entity.setHttpMethod(request.httpMethod() != null ? request.httpMethod() : io.openpulsechecker.domain.HttpMethod.GET);
            entity.setExpectedResponseKeyword(request.expectedResponseKeyword() != null ? request.expectedResponseKeyword().trim() : null);
        } else {
            entity.setHttpMethod(null);
            entity.setExpectedResponseKeyword(null);
        }

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
        validateTargetUrl(request.targetUrl(), request.type());

        MonitorEntity entity = getEntity(id);
        entity.setName(request.name().trim());
        entity.setType(request.type());
        entity.setTargetUrl(request.targetUrl().trim());
        entity.setIntervalSec(request.intervalSec());
        entity.setEnabled(request.enabled());
        entity.setTimeoutMs(request.timeoutMs());

        if (request.type() == MonitorType.HTTP) {
            entity.setHttpMethod(request.httpMethod() != null ? request.httpMethod() : io.openpulsechecker.domain.HttpMethod.GET);
            entity.setExpectedResponseKeyword(request.expectedResponseKeyword() != null ? request.expectedResponseKeyword().trim() : null);
        } else {
            entity.setHttpMethod(null);
            entity.setExpectedResponseKeyword(null);
        }

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

    @Transactional
    public void delete(UUID id) {
        MonitorEntity entity = getEntity(id);

        long statusPageBindings = statusPageMonitorRepository.countByMonitorId(id);
        long checkHistoryCount = checkResultRepository.countByMonitorId(id);
        long incidentHistoryCount = incidentRepository.countByMonitorId(id);

        if (checkHistoryCount > 0 || incidentHistoryCount > 0) {
            throw new MonitorDeletionBlockedException(
                    "Monitor deletion blocked: historical references exist (checkResults=" + checkHistoryCount
                            + ", incidents=" + incidentHistoryCount
                            + "). Remove related history first or archive the monitor by disabling it.");
        }

        monitorRepository.delete(entity);
        auditService.log(
                "MONITOR_DELETE",
                "monitor:" + entity.getId(),
                "SUCCESS",
                "name=" + entity.getName() + ", statusPageBindingsDetached=" + statusPageBindings);
    }

    private void validateTargetUrl(String rawUrl, MonitorType type) {
        if (type == MonitorType.TCP) {
            try {
                String[] parts = rawUrl.split(":");
                if (parts.length != 2) throw new IllegalArgumentException("TCP target must be host:port");
                Integer.parseInt(parts[1]);
            } catch (Exception e) {
                throw new IllegalArgumentException("TCP target must be host:port (e.g. localhost:5432)");
            }
            return;
        }

        URI uri = URI.create(rawUrl);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only HTTP/HTTPS target URLs are allowed for HTTP/PING monitors.");
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
                entity.getHttpMethod(),
                entity.getExpectedResponseKeyword(),
                latestCheck != null ? latestCheck.getCheckedAt() : null,
                latestCheck != null ? latestCheck.getStatus() : null,
                latestCheck != null ? latestCheck.getStatusCode() : null,
                latestCheck != null ? latestCheck.getLatencyMs() : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
