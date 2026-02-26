package io.openpulsechecker.alerting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispatched_alerts")
public class DispatchedAlertEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "monitor_id", nullable = false)
    private UUID monitorId;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public UUID getMonitorId() { return monitorId; }
    public void setMonitorId(UUID monitorId) { this.monitorId = monitorId; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
