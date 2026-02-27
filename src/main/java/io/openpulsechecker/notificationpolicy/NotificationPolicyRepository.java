package io.openpulsechecker.notificationpolicy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPolicyRepository extends JpaRepository<NotificationPolicyEntity, UUID> {
    Optional<NotificationPolicyEntity> findByScopeTypeAndScopeRefId(NotificationPolicyScopeType scopeType, UUID scopeRefId);
    Optional<NotificationPolicyEntity> findByScopeType(NotificationPolicyScopeType scopeType);
    List<NotificationPolicyEntity> findByScopeTypeAndScopeRefIdIn(NotificationPolicyScopeType scopeType, List<UUID> scopeRefIds);
}
