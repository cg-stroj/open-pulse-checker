package io.openpulsechecker.alerting;

import io.micrometer.core.instrument.MeterRegistry;
import io.openpulsechecker.audit.AuditService;
import io.openpulsechecker.service.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertDeadLetterService {

    private final AlertDeadLetterRepository repository;
    private final AlertDispatchService alertDispatchService;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public AlertDeadLetterService(AlertDeadLetterRepository repository,
                                  AlertDispatchService alertDispatchService,
                                  AuditService auditService,
                                  MeterRegistry meterRegistry,
                                  Clock clock) {
        this.repository = repository;
        this.alertDispatchService = alertDispatchService;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Transactional
    public void replay(UUID id) {
        AlertDeadLetterEntity item = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DLQ item not found: " + id));

        AlertEvent event = new AlertEvent(
                AlertEventType.valueOf(item.getEventType()),
                item.getMonitorId(),
                item.getIncidentId(),
                item.getCreatedAt(),
                item.getPayload());

        alertDispatchService.dispatch(event);

        item.setReplayedAt(Instant.now(clock));
        item.setReplayedBy("admin");
        item.setReplayResult("SUCCESS");
        repository.save(item);
        meterRegistry.counter("openpulse.alerts.dlq.replay", "result", "success").increment();
        auditService.log("ALERT_DLQ_REPLAY", "dlq:" + id, "SUCCESS", null);
    }
}
