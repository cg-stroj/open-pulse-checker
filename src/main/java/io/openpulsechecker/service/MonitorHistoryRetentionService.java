package io.openpulsechecker.service;

import io.openpulsechecker.schedulerlock.LockAcquireOutcome;
import io.openpulsechecker.schedulerlock.SchedulerLockService;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.IncidentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MonitorHistoryRetentionService {

    static final int RETENTION_DAYS = 30;
    private static final Logger log = LoggerFactory.getLogger(MonitorHistoryRetentionService.class);
    private static final String LOCK_NAME = "monitor-history-retention-cleanup";

    private final CheckResultRepository checkResultRepository;
    private final IncidentRepository incidentRepository;
    private final SchedulerLockService schedulerLockService;
    private final Clock clock;
    private final String ownerId = UUID.randomUUID().toString();
    private final Duration leaseDuration = Duration.ofMinutes(5);

    public MonitorHistoryRetentionService(CheckResultRepository checkResultRepository,
                                         IncidentRepository incidentRepository,
                                         SchedulerLockService schedulerLockService,
                                         Clock clock) {
        this.checkResultRepository = checkResultRepository;
        this.incidentRepository = incidentRepository;
        this.schedulerLockService = schedulerLockService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${openpulse.retention.cleanup-interval-ms:3600000}")
    public void cleanupExpiredHistory() {
        LockAcquireOutcome outcome = schedulerLockService.acquire(LOCK_NAME, ownerId, leaseDuration);
        if (outcome == LockAcquireOutcome.CONTENDED) {
            return;
        }

        try {
            if (!schedulerLockService.renew(LOCK_NAME, ownerId, leaseDuration)) {
                return;
            }
            purgeOlderThan(cutoffInstant());
        } catch (Exception ex) {
            log.error("Monitor history retention cleanup failed", ex);
        } finally {
            schedulerLockService.release(LOCK_NAME, ownerId);
        }
    }

    @Transactional
    void purgeOlderThan(Instant cutoff) {
        int deletedCheckResults = checkResultRepository.deleteByCheckedAtBefore(cutoff);
        int deletedIncidents = incidentRepository.deleteResolvedBefore(cutoff);

        if (deletedCheckResults > 0 || deletedIncidents > 0) {
            log.info("Retention cleanup removed {} check_results and {} incidents older than {}",
                    deletedCheckResults, deletedIncidents, cutoff);
        }
    }

    Instant cutoffInstant() {
        return clock.instant().minus(Duration.ofDays(RETENTION_DAYS));
    }
}
