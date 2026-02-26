package io.openpulsechecker.alerting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_dead_letters")
public class AlertDeadLetterEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String eventType;
    @Column(nullable = false)
    private UUID monitorId;
    @Column
    private UUID incidentId;
    @Column(nullable = false, length = 4096)
    private String payload;
    @Column(name = "failure_reason", nullable = false, length = 2048)
    private String failureReason;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private Instant createdAt;
    @Column
    private Instant replayedAt;
    @Column
    private String replayedBy;
    @Column
    private String replayResult;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public UUID getMonitorId() { return monitorId; }
    public void setMonitorId(UUID monitorId) { this.monitorId = monitorId; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getReplayedAt() { return replayedAt; }
    public void setReplayedAt(Instant replayedAt) { this.replayedAt = replayedAt; }
    public String getReplayedBy() { return replayedBy; }
    public void setReplayedBy(String replayedBy) { this.replayedBy = replayedBy; }
    public String getReplayResult() { return replayResult; }
    public void setReplayResult(String replayResult) { this.replayResult = replayResult; }
}
