package io.openpulsechecker.schedulerlock;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchedulerLockRepository extends JpaRepository<SchedulerLockEntity, String> {

    @Modifying
    @Query("""
        update SchedulerLockEntity l
           set l.ownerId = :ownerId,
               l.leaseUntil = :leaseUntil,
               l.updatedAt = :now
         where l.lockName = :lockName
           and (l.ownerId = :ownerId or l.leaseUntil < :now)
        """)
    int tryAcquireOrSteal(@Param("lockName") String lockName,
                          @Param("ownerId") String ownerId,
                          @Param("leaseUntil") Instant leaseUntil,
                          @Param("now") Instant now);

    @Modifying
    @Query("""
        update SchedulerLockEntity l
           set l.leaseUntil = :leaseUntil,
               l.updatedAt = :now
         where l.lockName = :lockName
           and l.ownerId = :ownerId
           and l.leaseUntil >= :now
        """)
    int renew(@Param("lockName") String lockName,
              @Param("ownerId") String ownerId,
              @Param("leaseUntil") Instant leaseUntil,
              @Param("now") Instant now);

    @Modifying
    int deleteByLockNameAndOwnerId(String lockName, String ownerId);
}
