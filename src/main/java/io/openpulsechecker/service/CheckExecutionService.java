package io.openpulsechecker.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.openpulsechecker.api.CheckResultResponse;
import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.audit.AuditService;
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
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    public CheckExecutionService(
            MonitorService monitorService,
            HttpCheckClient httpCheckClient,
            CheckResultRepository checkResultRepository,
            IncidentService incidentService,
            AuditService auditService,
            MeterRegistry meterRegistry
    ) {
        this.monitorService = monitorService;
        this.httpCheckClient = httpCheckClient;
        this.checkResultRepository = checkResultRepository;
        this.incidentService = incidentService;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
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

        auditService.log("MONITOR_RUN_CHECK", "monitor:" + monitorId, "SUCCESS", "status=" + saved.getStatus());
        meterRegistry.counter("openpulse.checks.executed", "status", saved.getStatus().name()).increment();
        Timer.builder("openpulse.checks.latency")
                .description("Observed monitor check latency from target response")
                .tag("status", saved.getStatus().name())
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(Math.max(0L, saved.getLatencyMs() == null ? 0L : saved.getLatencyMs())));

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
