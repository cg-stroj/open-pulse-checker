package io.openpulsechecker.notificationpolicy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_policies")
public class NotificationPolicyEntity {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationPolicyScopeType scopeType;
    @Column
    private UUID scopeRefId;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false)
    private int cooldownSeconds;
    @Column(nullable = false)
    private int dedupSeconds;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @PrePersist
    void prePersist() { if (id == null) { id = UUID.randomUUID(); } Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public NotificationPolicyScopeType getScopeType() { return scopeType; }
    public void setScopeType(NotificationPolicyScopeType scopeType) { this.scopeType = scopeType; }
    public UUID getScopeRefId() { return scopeRefId; }
    public void setScopeRefId(UUID scopeRefId) { this.scopeRefId = scopeRefId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(int cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    public int getDedupSeconds() { return dedupSeconds; }
    public void setDedupSeconds(int dedupSeconds) { this.dedupSeconds = dedupSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
