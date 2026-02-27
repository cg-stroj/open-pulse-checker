package io.openpulsechecker.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MonitorRepository extends JpaRepository<MonitorEntity, UUID>, JpaSpecificationExecutor<MonitorEntity> {
    List<MonitorEntity> findByEnabledTrue();
}
