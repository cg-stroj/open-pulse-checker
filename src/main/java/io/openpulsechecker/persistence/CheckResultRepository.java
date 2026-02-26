package io.openpulsechecker.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckResultRepository extends JpaRepository<CheckResultEntity, UUID> {
    Optional<CheckResultEntity> findTopByMonitorIdOrderByCheckedAtDesc(UUID monitorId);
}
