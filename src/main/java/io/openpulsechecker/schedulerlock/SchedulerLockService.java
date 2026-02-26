package io.openpulsechecker.schedulerlock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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
    public LockAcquireOutcome acquire(String lockName, String ownerId, Duration leaseDuration) {
        Instant now = clock.instant();
        Instant leaseUntil = now.plus(leaseDuration);
        Optional<SchedulerLockEntity> existing = repository.findById(lockName);

        if (repository.tryAcquireOrSteal(lockName, ownerId, leaseUntil, now) > 0) {
            return wasStolen(existing, ownerId, now) ? LockAcquireOutcome.STOLEN : LockAcquireOutcome.ACQUIRED;
        }

        SchedulerLockEntity entity = new SchedulerLockEntity();
        entity.setLockName(lockName);
        entity.setOwnerId(ownerId);
        entity.setLeaseUntil(leaseUntil);
        entity.setUpdatedAt(now);
        try {
            repository.save(entity);
            return LockAcquireOutcome.ACQUIRED;
        } catch (DataIntegrityViolationException ignored) {
            Optional<SchedulerLockEntity> contender = repository.findById(lockName);
            if (repository.tryAcquireOrSteal(lockName, ownerId, leaseUntil, now) > 0) {
                return wasStolen(contender, ownerId, now) ? LockAcquireOutcome.STOLEN : LockAcquireOutcome.ACQUIRED;
            }
            return LockAcquireOutcome.CONTENDED;
        }
    }

    @Transactional
    public boolean renew(String lockName, String ownerId, Duration leaseDuration) {
        Instant now = clock.instant();
        Instant leaseUntil = now.plus(leaseDuration);
        return repository.renew(lockName, ownerId, leaseUntil, now) > 0;
    }

    @Transactional
    public boolean release(String lockName, String ownerId) {
        return repository.deleteByLockNameAndOwnerId(lockName, ownerId) > 0;
    }

    private boolean wasStolen(Optional<SchedulerLockEntity> existing, String ownerId, Instant now) {
        return existing.isPresent()
                && !existing.get().getOwnerId().equals(ownerId)
                && existing.get().getLeaseUntil().isBefore(now);
    }
}
