package io.openpulsechecker.notificationpolicy;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRouteRuleRepository extends JpaRepository<NotificationRouteRuleEntity, UUID> {
    List<NotificationRouteRuleEntity> findByPolicyId(UUID policyId);
    void deleteByPolicyId(UUID policyId);
}
