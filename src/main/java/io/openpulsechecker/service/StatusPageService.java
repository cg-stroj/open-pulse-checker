package io.openpulsechecker.service;

import io.openpulsechecker.api.CreateStatusPageRequest;
import io.openpulsechecker.api.PublicStatusPageResponse;
import io.openpulsechecker.api.StatusPageResponse;
import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.domain.StatusPageOverallStatus;
import io.openpulsechecker.persistence.CheckResultEntity;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.IncidentEntity;
import io.openpulsechecker.persistence.IncidentRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import io.openpulsechecker.persistence.StatusPageEntity;
import io.openpulsechecker.persistence.StatusPageMonitorEntity;
import io.openpulsechecker.persistence.StatusPageMonitorRepository;
import io.openpulsechecker.persistence.StatusPageRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatusPageService {

    private static final int TIMELINE_LIMIT = 20;

    private final StatusPageRepository statusPageRepository;
    private final StatusPageMonitorRepository statusPageMonitorRepository;
    private final MonitorRepository monitorRepository;
    private final CheckResultRepository checkResultRepository;
    private final IncidentRepository incidentRepository;

    public StatusPageService(StatusPageRepository statusPageRepository,
                             StatusPageMonitorRepository statusPageMonitorRepository,
                             MonitorRepository monitorRepository,
                             CheckResultRepository checkResultRepository,
                             IncidentRepository incidentRepository) {
        this.statusPageRepository = statusPageRepository;
        this.statusPageMonitorRepository = statusPageMonitorRepository;
        this.monitorRepository = monitorRepository;
        this.checkResultRepository = checkResultRepository;
        this.incidentRepository = incidentRepository;
    }

    @Transactional
    public StatusPageResponse create(CreateStatusPageRequest request) {
        String slug = request.slug().trim();
        if (statusPageRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Status page slug already exists.");
        }

        StatusPageEntity entity = new StatusPageEntity();
        entity.setName(request.name().trim());
        entity.setSlug(slug);
        entity.setPublic(Boolean.TRUE.equals(request.isPublic()));
        StatusPageEntity saved = statusPageRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StatusPageResponse> list() {
        return statusPageRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<StatusPageResponse> listPage(String q, Boolean isPublic, Pageable pageable) {
        Specification<StatusPageEntity> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (isPublic != null) {
                predicate = cb.and(predicate, cb.equal(root.get("isPublic"), isPublic));
            }
            String normalizedQ = normalizeQuery(q);
            if (normalizedQ != null) {
                String like = "%" + normalizedQ.toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("slug")), like)
                ));
            }
            return predicate;
        };

        Page<StatusPageEntity> page = statusPageRepository.findAll(spec, pageable);
        List<StatusPageResponse> items = page.getContent().stream().map(this::toResponse).toList();
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    @Transactional
    public List<PublicStatusPageResponse.PublicMonitorSummary> attachMonitors(UUID statusPageId, List<UUID> monitorIds) {
        StatusPageEntity page = getPage(statusPageId);
        Set<UUID> uniqueMonitorIds = Set.copyOf(monitorIds);
        if (uniqueMonitorIds.size() != monitorIds.size()) {
            throw new IllegalArgumentException("Duplicate monitor IDs are not allowed.");
        }
        List<MonitorEntity> monitors = monitorRepository.findAllById(monitorIds);
        if (monitors.size() != uniqueMonitorIds.size()) {
            throw new IllegalArgumentException("One or more monitor IDs do not exist.");
        }

        statusPageMonitorRepository.deleteByStatusPageId(page.getId());
        int order = 0;
        for (UUID monitorId : monitorIds) {
            StatusPageMonitorEntity link = new StatusPageMonitorEntity();
            link.setStatusPageId(page.getId());
            link.setMonitorId(monitorId);
            link.setDisplayOrder(order++);
            statusPageMonitorRepository.save(link);
        }

        return buildMonitorSummaries(page.getId());
    }

    @Transactional
    public void removeMonitor(UUID statusPageId, UUID monitorId) {
        getPage(statusPageId);
        statusPageMonitorRepository.deleteByStatusPageIdAndMonitorId(statusPageId, monitorId);
    }

    @Transactional(readOnly = true)
    public PublicStatusPageResponse getPublicBySlug(String slug) {
        StatusPageEntity page = statusPageRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Status page not found."));
        if (!page.isPublic()) {
            throw new ResourceNotFoundException("Status page not found.");
        }

        List<PublicStatusPageResponse.PublicMonitorSummary> summaries = buildMonitorSummaries(page.getId());
        StatusPageOverallStatus overall = deriveOverallStatus(summaries);

        Map<UUID, String> monitorNames = new HashMap<>();
        for (PublicStatusPageResponse.PublicMonitorSummary summary : summaries) {
            monitorNames.put(summary.monitorId(), summary.monitorName());
        }

        List<PublicStatusPageResponse.IncidentTimelineItem> incidents = fetchTimeline(monitorNames);

        return new PublicStatusPageResponse(toResponse(page), overall, summaries, incidents);
    }

    StatusPageOverallStatus deriveOverallStatus(Collection<PublicStatusPageResponse.PublicMonitorSummary> summaries) {
        if (summaries.isEmpty()) {
            return StatusPageOverallStatus.OPERATIONAL;
        }
        boolean hasUnknown = false;
        for (PublicStatusPageResponse.PublicMonitorSummary summary : summaries) {
            if (summary.currentStatus() == CheckStatus.DOWN) {
                return StatusPageOverallStatus.OUTAGE;
            }
            if (summary.currentStatus() == CheckStatus.UNKNOWN) {
                hasUnknown = true;
            }
        }
        return hasUnknown ? StatusPageOverallStatus.DEGRADED : StatusPageOverallStatus.OPERATIONAL;
    }

    private List<PublicStatusPageResponse.PublicMonitorSummary> buildMonitorSummaries(UUID statusPageId) {
        List<StatusPageMonitorEntity> links = statusPageMonitorRepository.findByStatusPageIdOrderByDisplayOrderAsc(statusPageId);
        if (links.isEmpty()) {
            return List.of();
        }

        List<UUID> monitorIds = links.stream().map(StatusPageMonitorEntity::getMonitorId).toList();
        Map<UUID, MonitorEntity> monitorById = monitorRepository.findAllById(monitorIds).stream()
                .collect(HashMap::new, (m, v) -> m.put(v.getId(), v), HashMap::putAll);
        Map<UUID, CheckResultEntity> latestResultByMonitor = checkResultRepository.findLatestForMonitorIds(monitorIds).stream()
                .collect(HashMap::new, (m, v) -> m.put(v.getMonitorId(), v), HashMap::putAll);

        List<PublicStatusPageResponse.PublicMonitorSummary> summaries = new ArrayList<>();
        for (StatusPageMonitorEntity link : links) {
            MonitorEntity monitor = monitorById.get(link.getMonitorId());
            if (monitor == null) {
                continue;
            }
            CheckResultEntity check = latestResultByMonitor.get(link.getMonitorId());
            summaries.add(new PublicStatusPageResponse.PublicMonitorSummary(
                    monitor.getId(),
                    monitor.getName(),
                    link.getDisplayOrder(),
                    check == null ? CheckStatus.UNKNOWN : check.getStatus(),
                    check == null ? null : check.getStatusCode(),
                    check == null ? null : check.getLatencyMs(),
                    check == null ? null : check.getCheckedAt()
            ));
        }
        summaries.sort(Comparator.comparingInt(PublicStatusPageResponse.PublicMonitorSummary::displayOrder));
        return summaries;
    }

    private List<PublicStatusPageResponse.IncidentTimelineItem> fetchTimeline(Map<UUID, String> monitorNames) {
        if (monitorNames.isEmpty()) {
            return List.of();
        }
        List<IncidentEntity> incidents = incidentRepository.findByMonitorIdInOrderByOpenedAtDesc(
                monitorNames.keySet(),
                PageRequest.of(0, TIMELINE_LIMIT)
        );
        return incidents.stream().map(incident -> new PublicStatusPageResponse.IncidentTimelineItem(
                incident.getId(),
                incident.getMonitorId(),
                monitorNames.getOrDefault(incident.getMonitorId(), "Unknown monitor"),
                incident.getState(),
                incident.getOpenedAt(),
                incident.getResolvedAt(),
                incident.getReason()
        )).toList();
    }

    private StatusPageEntity getPage(UUID id) {
        return statusPageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Status page not found: " + id));
    }

    private String normalizeQuery(String q) {
        if (q == null) return null;
        String trimmed = q.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private StatusPageResponse toResponse(StatusPageEntity page) {
        return new StatusPageResponse(page.getId(), page.getName(), page.getSlug(), page.isPublic(), page.getCreatedAt(), page.getUpdatedAt());
    }
}
