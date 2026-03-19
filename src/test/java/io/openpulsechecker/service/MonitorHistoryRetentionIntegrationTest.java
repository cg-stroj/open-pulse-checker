package io.openpulsechecker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.domain.IncidentState;
import io.openpulsechecker.domain.MonitorType;
import io.openpulsechecker.incident.IncidentManualAction;
import io.openpulsechecker.incident.IncidentManualEventEntity;
import io.openpulsechecker.incident.IncidentManualEventRepository;
import io.openpulsechecker.persistence.CheckResultEntity;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.IncidentEntity;
import io.openpulsechecker.persistence.IncidentRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import io.openpulsechecker.support.H2TestDatabaseSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MonitorHistoryRetentionIntegrationTest extends H2TestDatabaseSupport {

    @Autowired private MonitorHistoryRetentionService retentionService;
    @Autowired private MonitorRepository monitorRepository;
    @Autowired private CheckResultRepository checkResultRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private IncidentManualEventRepository incidentManualEventRepository;

    @BeforeEach
    void resetDb() {
        incidentManualEventRepository.deleteAll();
        incidentRepository.deleteAll();
        checkResultRepository.deleteAll();
        monitorRepository.deleteAll();
    }

    @Test
    void purgeRemovesOnlyRecordsOlderThanThirtyDayCutoff() {
        MonitorEntity monitor = new MonitorEntity();
        monitor.setId(UUID.randomUUID());
        monitor.setName("Retention monitor");
        monitor.setType(MonitorType.HTTP);
        monitor.setTargetUrl("https://example.com");
        monitor.setIntervalSec(60);
        monitor.setEnabled(true);
        monitor.setTimeoutMs(1000);
        monitorRepository.save(monitor);

        Instant cutoff = Instant.parse("2026-02-17T11:00:00Z");

        CheckResultEntity oldCheck = new CheckResultEntity();
        oldCheck.setMonitorId(monitor.getId());
        oldCheck.setStatus(CheckStatus.DOWN);
        oldCheck.setCheckedAt(cutoff.minusSeconds(1));
        checkResultRepository.save(oldCheck);

        CheckResultEntity boundaryCheck = new CheckResultEntity();
        boundaryCheck.setMonitorId(monitor.getId());
        boundaryCheck.setStatus(CheckStatus.UP);
        boundaryCheck.setCheckedAt(cutoff);
        checkResultRepository.save(boundaryCheck);

        IncidentEntity oldResolved = new IncidentEntity();
        oldResolved.setMonitorId(monitor.getId());
        oldResolved.setState(IncidentState.RESOLVED);
        oldResolved.setOpenedAt(cutoff.minusSeconds(3600));
        oldResolved.setResolvedAt(cutoff.minusSeconds(1));
        oldResolved.setReason("old resolved");
        IncidentEntity savedOldResolved = incidentRepository.save(oldResolved);

        IncidentManualEventEntity event = new IncidentManualEventEntity();
        event.setIncidentId(savedOldResolved.getId());
        event.setAction(IncidentManualAction.RESOLVED_MANUALLY);
        event.setActor("ops");
        event.setReason("cleanup test");
        event.setFromState(IncidentState.OPEN);
        event.setToState(IncidentState.RESOLVED);
        event.setOccurredAt(cutoff.minusSeconds(1));
        incidentManualEventRepository.save(event);

        IncidentEntity boundaryResolved = new IncidentEntity();
        boundaryResolved.setMonitorId(monitor.getId());
        boundaryResolved.setState(IncidentState.RESOLVED);
        boundaryResolved.setOpenedAt(cutoff.minusSeconds(120));
        boundaryResolved.setResolvedAt(cutoff);
        boundaryResolved.setReason("boundary resolved");
        incidentRepository.save(boundaryResolved);

        IncidentEntity oldOpen = new IncidentEntity();
        oldOpen.setMonitorId(monitor.getId());
        oldOpen.setState(IncidentState.OPEN);
        oldOpen.setOpenedAt(cutoff.minusSeconds(86400));
        oldOpen.setReason("still open");
        incidentRepository.save(oldOpen);

        retentionService.purgeOlderThan(cutoff);

        assertEquals(1, checkResultRepository.count(), "only cutoff-excluded check result should be removed");
        assertEquals(2, incidentRepository.count(), "only resolved incidents older than cutoff should be removed");
        assertEquals(0, incidentManualEventRepository.count(), "manual events should cascade-delete with incident purge");
    }
}
