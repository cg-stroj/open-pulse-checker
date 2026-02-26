package io.openpulsechecker.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorRepository extends JpaRepository<MonitorEntity, UUID> {
    List<MonitorEntity> findByEnabledTrue();
}
