package io.openpulsechecker.persistence;

import io.openpulsechecker.domain.IncidentState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<IncidentEntity, UUID> {

    Optional<IncidentEntity> findTopByMonitorIdAndStateOrderByOpenedAtDesc(UUID monitorId, IncidentState state);
}
