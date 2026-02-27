package io.openpulsechecker.maintenance;

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
@Table(name = "maintenance_windows")
public class MaintenanceWindowEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MaintenanceWindowScopeType scopeType;

    @Column
    private UUID scopeRefId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MaintenanceWindowType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MaintenancePolicy policy;

    @Column(nullable = false)
    private boolean enabled;

    @Column
    private Instant startAt;

    @Column
    private Instant endAt;

    @Column(length = 64)
    private String timezone;

    @Column(length = 128)
    private String recurringDays;

    @Column(length = 8)
    private String recurringStartTime;

    @Column(length = 8)
    private String recurringEndTime;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public MaintenanceWindowScopeType getScopeType() { return scopeType; }
    public void setScopeType(MaintenanceWindowScopeType scopeType) { this.scopeType = scopeType; }
    public UUID getScopeRefId() { return scopeRefId; }
    public void setScopeRefId(UUID scopeRefId) { this.scopeRefId = scopeRefId; }
    public MaintenanceWindowType getType() { return type; }
    public void setType(MaintenanceWindowType type) { this.type = type; }
    public MaintenancePolicy getPolicy() { return policy; }
    public void setPolicy(MaintenancePolicy policy) { this.policy = policy; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getRecurringDays() { return recurringDays; }
    public void setRecurringDays(String recurringDays) { this.recurringDays = recurringDays; }
    public String getRecurringStartTime() { return recurringStartTime; }
    public void setRecurringStartTime(String recurringStartTime) { this.recurringStartTime = recurringStartTime; }
    public String getRecurringEndTime() { return recurringEndTime; }
    public void setRecurringEndTime(String recurringEndTime) { this.recurringEndTime = recurringEndTime; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
