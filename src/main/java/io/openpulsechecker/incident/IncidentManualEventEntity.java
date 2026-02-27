package io.openpulsechecker.incident;

import io.openpulsechecker.domain.IncidentState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_manual_events")
public class IncidentManualEventEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IncidentManualAction action;

    @Column(nullable = false, length = 120)
    private String actor;

    @Column(nullable = false, length = 2048)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentState fromState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentState toState;

    @Column(nullable = false)
    private Instant occurredAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (occurredAt == null) occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }
    public IncidentManualAction getAction() { return action; }
    public void setAction(IncidentManualAction action) { this.action = action; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public IncidentState getFromState() { return fromState; }
    public void setFromState(IncidentState fromState) { this.fromState = fromState; }
    public IncidentState getToState() { return toState; }
    public void setToState(IncidentState toState) { this.toState = toState; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
