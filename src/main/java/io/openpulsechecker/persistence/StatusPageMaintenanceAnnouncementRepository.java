package io.openpulsechecker.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusPageMaintenanceAnnouncementRepository extends JpaRepository<StatusPageMaintenanceAnnouncementEntity, UUID> {
    List<StatusPageMaintenanceAnnouncementEntity> findByStatusPageIdOrderByPublishAtDesc(UUID statusPageId);

    List<StatusPageMaintenanceAnnouncementEntity> findByStatusPageIdAndIsPublicIsTrueAndPublishAtLessThanEqualOrderByPublishAtDesc(
            UUID statusPageId,
            Instant publishAt
    );
}
