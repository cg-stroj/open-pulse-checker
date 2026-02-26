package io.openpulsechecker.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openpulsechecker.persistence.CheckResultEntity;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
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

    @Mock
    private MonitorRepository monitorRepository;
    @Mock
    private CheckResultRepository checkResultRepository;
    @Mock
    private CheckExecutionService checkExecutionService;

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
                monitorRepository,
                checkResultRepository,
                checkExecutionService,
                new QueuedExecutor(),
                Clock.fixed(Instant.parse("2026-02-26T22:01:00Z"), ZoneOffset.UTC)
        );

        assertFalse(scheduler.isDue(monitor, Instant.parse("2026-02-26T22:01:00Z")));
        assertTrue(scheduler.isDue(monitor, Instant.parse("2026-02-26T22:01:30Z")));
    }

    @Test
    void avoidsDuplicateConcurrentRunsForSameMonitor() {
        UUID monitorId = UUID.randomUUID();
        MonitorEntity monitor = new MonitorEntity();
        monitor.setId(monitorId);
        monitor.setIntervalSec(10);
        when(monitorRepository.findByEnabledTrue()).thenReturn(List.of(monitor));
        when(checkResultRepository.findTopByMonitorIdOrderByCheckedAtDesc(monitorId)).thenReturn(Optional.empty());

        QueuedExecutor executor = new QueuedExecutor();
        var scheduler = new MonitorCheckScheduler(
                monitorRepository,
                checkResultRepository,
                checkExecutionService,
                executor,
                Clock.fixed(Instant.parse("2026-02-26T22:01:00Z"), ZoneOffset.UTC)
        );

        scheduler.scheduleDueChecks();
        scheduler.scheduleDueChecks();

        verify(checkExecutionService, never()).runCheck(monitorId);
        assertTrue(executor.taskCount() == 1);

        executor.runAll();
        verify(checkExecutionService).runCheck(monitorId);
    }

    static class QueuedExecutor extends AbstractExecutorService {
        private final List<Runnable> tasks = new ArrayList<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.copyOf(tasks);
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int taskCount() {
            return tasks.size();
        }

        void runAll() {
            for (Runnable task : List.copyOf(tasks)) {
                task.run();
            }
            tasks.clear();
        }
    }
}
