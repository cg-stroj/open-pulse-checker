package io.openpulsechecker.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import io.openpulsechecker.schedulerlock.LockAcquireOutcome;
import io.openpulsechecker.schedulerlock.SchedulerLockService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonitorCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitorCheckScheduler.class);

    private final MonitorRepository monitorRepository;
    private final CheckResultRepository checkResultRepository;
    private final CheckExecutionService checkExecutionService;
    private final ExecutorService monitorCheckExecutor;
    private final SchedulerLockService schedulerLockService;
    private final Set<UUID> inFlightMonitorIds = ConcurrentHashMap.newKeySet();
    private final Clock clock;
    private final String ownerId = UUID.randomUUID().toString();
    private final Duration leaseDuration = Duration.ofSeconds(30);
    private final Counter lockAcquireSuccessCounter;
    private final Counter lockAcquireFailCounter;
    private final Counter lockAcquireStealCounter;
    private final Counter lockSkipCounter;
    private final Counter lockRenewFailedCounter;
    private final Counter localInFlightSkipCounter;

    public MonitorCheckScheduler(
            MonitorRepository monitorRepository,
            CheckResultRepository checkResultRepository,
            CheckExecutionService checkExecutionService,
            ExecutorService monitorCheckExecutor,
            SchedulerLockService schedulerLockService,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.monitorRepository = monitorRepository;
        this.checkResultRepository = checkResultRepository;
        this.checkExecutionService = checkExecutionService;
        this.monitorCheckExecutor = monitorCheckExecutor;
        this.schedulerLockService = schedulerLockService;
        this.clock = clock;
        this.lockAcquireSuccessCounter = meterRegistry.counter("openpulse.scheduler.lock.acquire.success");
        this.lockAcquireFailCounter = meterRegistry.counter("openpulse.scheduler.lock.acquire.fail");
        this.lockAcquireStealCounter = meterRegistry.counter("openpulse.scheduler.lock.acquire.steal");
        this.lockSkipCounter = meterRegistry.counter("openpulse.scheduler.execution.skip.lock");
        this.lockRenewFailedCounter = meterRegistry.counter("openpulse.scheduler.lock.renew.fail");
        this.localInFlightSkipCounter = meterRegistry.counter("openpulse.scheduler.execution.skip.local_inflight");
    }

    @Scheduled(fixedDelayString = "${openpulse.scheduler.poll-interval-ms:5000}")
    public void scheduleDueChecks() {
        Instant now = clock.instant();
        for (MonitorEntity monitor : monitorRepository.findByEnabledTrue()) {
            if (!isDue(monitor, now)) {
                continue;
            }
            dispatchIfNotRunning(monitor.getId());
        }
    }

    boolean isDue(MonitorEntity monitor, Instant now) {
        var lastCheck = checkResultRepository.findTopByMonitorIdOrderByCheckedAtDesc(monitor.getId());
        if (lastCheck.isEmpty()) {
            return true;
        }
        Instant dueAt = lastCheck.get().getCheckedAt().plusSeconds(Math.max(1, monitor.getIntervalSec()));
        return !dueAt.isAfter(now);
    }

    void dispatchIfNotRunning(UUID monitorId) {
        if (!inFlightMonitorIds.add(monitorId)) {
            localInFlightSkipCounter.increment();
            return;
        }

        String lockName = "monitor-check:" + monitorId;
        LockAcquireOutcome outcome = schedulerLockService.acquire(lockName, ownerId, leaseDuration);
        if (outcome == LockAcquireOutcome.CONTENDED) {
            lockAcquireFailCounter.increment();
            lockSkipCounter.increment();
            inFlightMonitorIds.remove(monitorId);
            return;
        }

        lockAcquireSuccessCounter.increment();
        if (outcome == LockAcquireOutcome.STOLEN) {
            lockAcquireStealCounter.increment();
            log.warn("Recovered stale scheduler lock {} for monitor {}", lockName, monitorId);
        }

        monitorCheckExecutor.submit(() -> {
            try {
                if (!schedulerLockService.renew(lockName, ownerId, leaseDuration)) {
                    lockRenewFailedCounter.increment();
                    lockSkipCounter.increment();
                    return;
                }
                if (!isStillDue(monitorId, clock.instant())) {
                    return;
                }
                checkExecutionService.runCheck(monitorId);
            } catch (Exception ex) {
                log.error("Scheduled check failed for monitor {}", monitorId, ex);
            } finally {
                schedulerLockService.release(lockName, ownerId);
                inFlightMonitorIds.remove(monitorId);
            }
        });
    }

    private boolean isStillDue(UUID monitorId, Instant now) {
        return monitorRepository.findById(monitorId)
                .filter(MonitorEntity::isEnabled)
                .filter(monitor -> isDue(monitor, now))
                .isPresent();
    }
}
