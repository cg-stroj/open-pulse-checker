package io.openpulsechecker.schedulerlock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulerLockService {

    private final SchedulerLockRepository repository;
    private final Clock clock;

    public SchedulerLockService(SchedulerLockRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean acquire(String lockName, String ownerId, Duration leaseDuration) {
        Instant now = clock.instant();
        Instant leaseUntil = now.plus(leaseDuration);

        if (repository.tryAcquireOrSteal(lockName, ownerId, leaseUntil, now) > 0) {
            return true;
        }

        SchedulerLockEntity entity = new SchedulerLockEntity();
        entity.setLockName(lockName);
        entity.setOwnerId(ownerId);
        entity.setLeaseUntil(leaseUntil);
        entity.setUpdatedAt(now);
        try {
            repository.save(entity);
            return true;
        } catch (DataIntegrityViolationException ignored) {
            return repository.tryAcquireOrSteal(lockName, ownerId, leaseUntil, now) > 0;
        }
    }

    @Transactional
    public boolean renew(String lockName, String ownerId, Duration leaseDuration) {
        Instant now = clock.instant();
        Instant leaseUntil = now.plus(leaseDuration);
        return repository.renew(lockName, ownerId, leaseUntil, now) > 0;
    }

    @Transactional
    public void release(String lockName, String ownerId) {
        repository.deleteByLockNameAndOwnerId(lockName, ownerId);
    }
}
