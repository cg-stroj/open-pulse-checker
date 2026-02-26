package io.openpulsechecker.persistence;

import io.openpulsechecker.domain.CheckStatus;
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
@Table(name = "check_results")
public class CheckResultEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID monitorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckStatus status;

    @Column
    private Integer statusCode;

    @Column
    private Long latencyMs;

    @Column(nullable = false)
    private Instant checkedAt;

    @Column(length = 2048)
    private String error;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (checkedAt == null) {
            checkedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getMonitorId() { return monitorId; }
    public void setMonitorId(UUID monitorId) { this.monitorId = monitorId; }
    public CheckStatus getStatus() { return status; }
    public void setStatus(CheckStatus status) { this.status = status; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
