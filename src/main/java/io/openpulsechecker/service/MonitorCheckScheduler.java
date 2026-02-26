package io.openpulsechecker.service;

import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import java.time.Clock;
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
    private final Set<UUID> inFlightMonitorIds = ConcurrentHashMap.newKeySet();
    private final Clock clock;

    public MonitorCheckScheduler(
            MonitorRepository monitorRepository,
            CheckResultRepository checkResultRepository,
            CheckExecutionService checkExecutionService,
            ExecutorService monitorCheckExecutor,
            Clock clock
    ) {
        this.monitorRepository = monitorRepository;
        this.checkResultRepository = checkResultRepository;
        this.checkExecutionService = checkExecutionService;
        this.monitorCheckExecutor = monitorCheckExecutor;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${openpulse.scheduler.poll-interval-ms}")
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
            return;
        }

        monitorCheckExecutor.submit(() -> {
            try {
                checkExecutionService.runCheck(monitorId);
            } catch (Exception ex) {
                log.error("Scheduled check failed for monitor {}", monitorId, ex);
            } finally {
                inFlightMonitorIds.remove(monitorId);
            }
        });
    }
}
