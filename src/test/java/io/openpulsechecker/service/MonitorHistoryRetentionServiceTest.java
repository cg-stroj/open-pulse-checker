package io.openpulsechecker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.IncidentRepository;
import io.openpulsechecker.schedulerlock.LockAcquireOutcome;
import io.openpulsechecker.schedulerlock.SchedulerLockService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitorHistoryRetentionServiceTest {

    @Mock private CheckResultRepository checkResultRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private SchedulerLockService schedulerLockService;

    @Test
    void purgeUsesFixedThirtyDayBoundaryAndDeleteOrder() {
        Instant now = Instant.parse("2026-03-19T11:00:00Z");
        var service = new MonitorHistoryRetentionService(
                checkResultRepository,
                incidentRepository,
                schedulerLockService,
                Clock.fixed(now, ZoneOffset.UTC));

        Instant cutoff = service.cutoffInstant();
        assertEquals(Instant.parse("2026-02-17T11:00:00Z"), cutoff);

        service.purgeOlderThan(cutoff);

        verify(checkResultRepository).deleteByCheckedAtBefore(cutoff);
        verify(incidentRepository).deleteResolvedBefore(cutoff);
    }

    @Test
    void scheduledCleanupSkipsWhenLockContended() {
        var service = new MonitorHistoryRetentionService(
                checkResultRepository,
                incidentRepository,
                schedulerLockService,
                Clock.fixed(Instant.parse("2026-03-19T11:00:00Z"), ZoneOffset.UTC));

        when(schedulerLockService.acquire(anyString(), anyString(), any()))
                .thenReturn(LockAcquireOutcome.CONTENDED);

        service.cleanupExpiredHistory();

        verify(checkResultRepository, never()).deleteByCheckedAtBefore(any());
        verify(incidentRepository, never()).deleteResolvedBefore(any());
        verify(schedulerLockService, never()).renew(anyString(), anyString(), any());
    }

    @Test
    void scheduledCleanupPurgesWhenLockAcquiredAndRenewed() {
        Instant now = Instant.parse("2026-03-19T11:00:00Z");
        var service = new MonitorHistoryRetentionService(
                checkResultRepository,
                incidentRepository,
                schedulerLockService,
                Clock.fixed(now, ZoneOffset.UTC));

        when(schedulerLockService.acquire(anyString(), anyString(), any()))
                .thenReturn(LockAcquireOutcome.ACQUIRED);
        when(schedulerLockService.renew(anyString(), anyString(), any()))
                .thenReturn(true);

        service.cleanupExpiredHistory();

        Instant cutoff = Instant.parse("2026-02-17T11:00:00Z");
        verify(checkResultRepository).deleteByCheckedAtBefore(cutoff);
        verify(incidentRepository).deleteResolvedBefore(cutoff);
        verify(schedulerLockService).release(anyString(), anyString());
    }
}
