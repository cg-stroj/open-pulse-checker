package io.openpulsechecker.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 120)
    private String username;
    @Column(nullable = false, length = 120)
    private String action;
    @Column(nullable = false, length = 255)
    private String target;
    @Column(nullable = false, length = 40)
    private String result;
    @Column(length = 2048)
    private String details;
    @Column(nullable = false)
    private Instant occurredAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (occurredAt == null) occurredAt = Instant.now();
    }

    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getUsername() { return username; }
    public String getResult() { return result; }
    public void setUsername(String username) { this.username = username; }
    public void setAction(String action) { this.action = action; }
    public void setTarget(String target) { this.target = target; }
    public void setResult(String result) { this.result = result; }
    public void setDetails(String details) { this.details = details; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
