package io.openpulsechecker.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CheckResultRepository extends JpaRepository<CheckResultEntity, UUID> {
    Optional<CheckResultEntity> findTopByMonitorIdOrderByCheckedAtDesc(UUID monitorId);

    long countByMonitorId(UUID monitorId);

    @Query("""
            select c from CheckResultEntity c
            where c.monitorId in :monitorIds
            and c.checkedAt = (
                select max(c2.checkedAt) from CheckResultEntity c2 where c2.monitorId = c.monitorId
            )
            """)
    List<CheckResultEntity> findLatestForMonitorIds(Collection<UUID> monitorIds);
}
