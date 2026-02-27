package io.openpulsechecker.incident;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentManualEventRepository extends JpaRepository<IncidentManualEventEntity, UUID> {
    List<IncidentManualEventEntity> findByIncidentIdOrderByOccurredAtAsc(UUID incidentId);
}
