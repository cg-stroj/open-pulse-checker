package io.openpulsechecker.persistence;

import io.openpulsechecker.domain.IncidentState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IncidentRepository extends JpaRepository<IncidentEntity, UUID>, JpaSpecificationExecutor<IncidentEntity> {

    Optional<IncidentEntity> findTopByMonitorIdAndStateOrderByOpenedAtDesc(UUID monitorId, IncidentState state);

    Optional<IncidentEntity> findTopByMonitorIdAndStateInOrderByOpenedAtDesc(UUID monitorId, Collection<IncidentState> states);

    List<IncidentEntity> findByMonitorIdInOrderByOpenedAtDesc(Collection<UUID> monitorIds, Pageable pageable);

    List<IncidentEntity> findAllByOrderByOpenedAtDesc();
}
