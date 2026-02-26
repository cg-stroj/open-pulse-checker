package io.openpulsechecker.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openpulsechecker.persistence.CheckResultEntity;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import io.openpulsechecker.schedulerlock.LockAcquireOutcome;
import io.openpulsechecker.schedulerlock.SchedulerLockService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitorCheckSchedulerTest {

    @Mock private MonitorRepository monitorRepository;
    @Mock private CheckResultRepository checkResultRepository;
    @Mock private CheckExecutionService checkExecutionService;
    @Mock private SchedulerLockService schedulerLockService;

    @Test
    void dueCheckUsesIntervalAgainstLastExecutionTime() {
        UUID monitorId = UUID.randomUUID();
        MonitorEntity monitor = new MonitorEntity();
        monitor.setId(monitorId);
        monitor.setIntervalSec(60);

        CheckResultEntity last = new CheckResultEntity();
        last.setCheckedAt(Instant.parse("2026-02-26T22:00:30Z"));
        when(checkResultRepository.findTopByMonitorIdOrderByCheckedAtDesc(monitorId)).thenReturn(Optional.of(last));

        var scheduler = new MonitorCheckScheduler(
                monitorRepository, checkResultRepository, checkExecutionService,
                new QueuedExecutor(), schedulerLockService,
                Clock.fixed(Instant.parse("2026-02-26T22:01:00Z"), ZoneOffset.UTC),
                new SimpleMeterRegistry());

        assertFalse(scheduler.isDue(monitor, Instant.parse("2026-02-26T22:01:00Z")));
        assertTrue(scheduler.isDue(monitor, Instant.parse("2026-02-26T22:01:30Z")));
    }

    @Test
    void avoidsDuplicateConcurrentRunsForSameMonitor() {
        UUID monitorId = UUID.randomUUID();
        MonitorEntity monitor = new MonitorEntity();
        monitor.setId(monitorId);
        monitor.setIntervalSec(10);
        monitor.setEnabled(true);
        when(monitorRepository.findByEnabledTrue()).thenReturn(List.of(monitor));
        when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));
        when(checkResultRepository.findTopByMonitorIdOrderByCheckedAtDesc(monitorId)).thenReturn(Optional.empty());
        when(schedulerLockService.acquire(anyString(), anyString(), any())).thenReturn(LockAcquireOutcome.ACQUIRED);
        when(schedulerLockService.renew(anyString(), anyString(), any())).thenReturn(true);

        QueuedExecutor executor = new QueuedExecutor();
        var scheduler = new MonitorCheckScheduler(
                monitorRepository, checkResultRepository, checkExecutionService,
                executor, schedulerLockService,
                Clock.fixed(Instant.parse("2026-02-26T22:01:00Z"), ZoneOffset.UTC),
                new SimpleMeterRegistry());

        scheduler.scheduleDueChecks();
        scheduler.scheduleDueChecks();

        verify(checkExecutionService, never()).runCheck(monitorId);
        assertTrue(executor.taskCount() == 1);

        executor.runAll();
        verify(checkExecutionService).runCheck(monitorId);
    }

    @Test
    void skipsExecutionWhenDistributedLockIsContended() {
        UUID monitorId = UUID.randomUUID();
        MonitorEntity monitor = new MonitorEntity();
        monitor.setId(monitorId);
        monitor.setEnabled(true);
        when(monitorRepository.findByEnabledTrue()).thenReturn(List.of(monitor));
        when(checkResultRepository.findTopByMonitorIdOrderByCheckedAtDesc(monitorId)).thenReturn(Optional.empty());
        when(schedulerLockService.acquire(anyString(), anyString(), any())).thenReturn(LockAcquireOutcome.CONTENDED);

        QueuedExecutor executor = new QueuedExecutor();
        var scheduler = new MonitorCheckScheduler(
                monitorRepository, checkResultRepository, checkExecutionService,
                executor, schedulerLockService,
                Clock.fixed(Instant.parse("2026-02-26T22:01:00Z"), ZoneOffset.UTC),
                new SimpleMeterRegistry());

        scheduler.scheduleDueChecks();

        assertTrue(executor.taskCount() == 0);
        verify(checkExecutionService, never()).runCheck(monitorId);
    }

    static class QueuedExecutor extends AbstractExecutorService {
        private final List<Runnable> tasks = new ArrayList<>();
        private boolean shutdown;
        public void shutdown() { shutdown = true; }
        public List<Runnable> shutdownNow() { shutdown = true; return List.copyOf(tasks); }
        public boolean isShutdown() { return shutdown; }
        public boolean isTerminated() { return shutdown; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
        public void execute(Runnable command) { tasks.add(command); }
        int taskCount() { return tasks.size(); }
        void runAll() { for (Runnable task : List.copyOf(tasks)) task.run(); tasks.clear(); }
    }
}
