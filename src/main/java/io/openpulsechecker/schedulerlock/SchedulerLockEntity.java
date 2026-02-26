package io.openpulsechecker.schedulerlock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "scheduler_locks")
public class SchedulerLockEntity {
    @Id
    @Column(name = "lock_name", nullable = false, length = 255)
    private String lockName;
    @Column(name = "owner_id", nullable = false, length = 128)
    private String ownerId;
    @Column(name = "lease_until", nullable = false)
    private Instant leaseUntil;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    public String getLockName() { return lockName; }
    public void setLockName(String lockName) { this.lockName = lockName; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
