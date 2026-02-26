package io.openpulsechecker.service;

import io.openpulsechecker.api.CheckResultResponse;
import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.persistence.CheckResultEntity;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckExecutionService {

    private final MonitorService monitorService;
    private final HttpCheckClient httpCheckClient;
    private final CheckResultRepository checkResultRepository;
    private final IncidentService incidentService;

    public CheckExecutionService(
            MonitorService monitorService,
            HttpCheckClient httpCheckClient,
            CheckResultRepository checkResultRepository,
            IncidentService incidentService
    ) {
        this.monitorService = monitorService;
        this.httpCheckClient = httpCheckClient;
        this.checkResultRepository = checkResultRepository;
        this.incidentService = incidentService;
    }

    @Transactional
    public CheckResultResponse runCheck(UUID monitorId) {
        MonitorEntity monitor = monitorService.getEntity(monitorId);
        var outcome = httpCheckClient.execute(monitor.getTargetUrl(), monitor.getTimeoutMs());

        CheckResultEntity result = new CheckResultEntity();
        result.setMonitorId(monitor.getId());
        result.setStatus(outcome.up() ? CheckStatus.UP : CheckStatus.DOWN);
        result.setStatusCode(outcome.statusCode());
        result.setLatencyMs(outcome.latencyMs());
        result.setCheckedAt(Instant.now());
        result.setError(outcome.error());

        CheckResultEntity saved = checkResultRepository.save(result);

        incidentService.applyTransition(
                monitor.getId(),
                saved.getStatus(),
                saved.getCheckedAt(),
                deriveReason(saved)
        );

        return new CheckResultResponse(
                saved.getId(),
                saved.getMonitorId(),
                saved.getStatus(),
                saved.getStatusCode(),
                saved.getLatencyMs(),
                saved.getCheckedAt(),
                saved.getError()
        );
    }

    private String deriveReason(CheckResultEntity result) {
        if (result.getError() != null && !result.getError().isBlank()) {
            return result.getError();
        }
        if (result.getStatusCode() != null) {
            return "HTTP status " + result.getStatusCode();
        }
        return "Unknown check failure";
    }
}
