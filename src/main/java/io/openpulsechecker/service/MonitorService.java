package io.openpulsechecker.service;

import io.openpulsechecker.api.CreateMonitorRequest;
import io.openpulsechecker.api.MonitorResponse;
import io.openpulsechecker.domain.MonitorType;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;

    public MonitorService(MonitorRepository monitorRepository) {
        this.monitorRepository = monitorRepository;
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

        return toResponse(monitorRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<MonitorResponse> listAll() {
        return monitorRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MonitorEntity getEntity(UUID id) {
        return monitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitor not found: " + id));
    }

    @Transactional(readOnly = true)
    public MonitorResponse get(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public MonitorResponse updateEnabled(UUID id, boolean enabled) {
        MonitorEntity entity = getEntity(id);
        entity.setEnabled(enabled);
        return toResponse(monitorRepository.save(entity));
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

    private MonitorResponse toResponse(MonitorEntity entity) {
        return new MonitorResponse(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getTargetUrl(),
                entity.getIntervalSec(),
                entity.isEnabled(),
                entity.getTimeoutMs(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
