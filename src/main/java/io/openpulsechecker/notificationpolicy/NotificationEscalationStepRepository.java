package io.openpulsechecker.notificationpolicy;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEscalationStepRepository extends JpaRepository<NotificationEscalationStepEntity, UUID> {
    List<NotificationEscalationStepEntity> findByPolicyIdOrderByStepOrderAsc(UUID policyId);
    void deleteByPolicyId(UUID policyId);
}
