package io.openpulsechecker.maintenance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindowEntity, UUID> {

    List<MaintenanceWindowEntity> findByEnabledTrueAndScopeType(MaintenanceWindowScopeType scopeType);

    List<MaintenanceWindowEntity> findByEnabledTrueAndScopeTypeAndScopeRefId(
            MaintenanceWindowScopeType scopeType,
            UUID scopeRefId
    );
}
