package io.openpulsechecker.notificationpolicy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "notification_escalation_steps")
public class NotificationEscalationStepEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID policyId;
    @Column(nullable = false)
    private int stepOrder;
    @Column(nullable = false)
    private int afterSeconds;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationSeverity minSeverity;
    @Column(nullable = false)
    private boolean webhookEnabled;
    @PrePersist
    void prePersist() { if (id == null) { id = UUID.randomUUID(); } }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPolicyId() { return policyId; }
    public void setPolicyId(UUID policyId) { this.policyId = policyId; }
    public int getStepOrder() { return stepOrder; }
    public void setStepOrder(int stepOrder) { this.stepOrder = stepOrder; }
    public int getAfterSeconds() { return afterSeconds; }
    public void setAfterSeconds(int afterSeconds) { this.afterSeconds = afterSeconds; }
    public NotificationSeverity getMinSeverity() { return minSeverity; }
    public void setMinSeverity(NotificationSeverity minSeverity) { this.minSeverity = minSeverity; }
    public boolean isWebhookEnabled() { return webhookEnabled; }
    public void setWebhookEnabled(boolean webhookEnabled) { this.webhookEnabled = webhookEnabled; }
}
