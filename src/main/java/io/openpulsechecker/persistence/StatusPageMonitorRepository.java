package io.openpulsechecker.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusPageMonitorRepository extends JpaRepository<StatusPageMonitorEntity, StatusPageMonitorId> {
    List<StatusPageMonitorEntity> findByStatusPageIdOrderByDisplayOrderAsc(UUID statusPageId);
    List<StatusPageMonitorEntity> findByMonitorId(UUID monitorId);
    long countByMonitorId(UUID monitorId);
    void deleteByStatusPageId(UUID statusPageId);
    void deleteByStatusPageIdAndMonitorId(UUID statusPageId, UUID monitorId);
}
