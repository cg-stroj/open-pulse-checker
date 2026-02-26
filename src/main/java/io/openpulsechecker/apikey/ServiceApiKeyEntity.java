package io.openpulsechecker.apikey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_api_keys")
public class ServiceApiKeyEntity {
    @Id
    private UUID id;
    @Column(name = "key_id", nullable = false, unique = true)
    private String keyId;
    @Column(name = "secret_hash", nullable = false, length = 128)
    private String secretHash;
    @Column(name = "role_name", nullable = false, length = 40)
    private String roleName;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column
    private Instant rotatedAt;
    @Column
    private String rotatedBy;
    @Column
    private Instant revokedAt;
    @Column
    private String revokedBy;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getSecretHash() { return secretHash; }
    public void setSecretHash(String secretHash) { this.secretHash = secretHash; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Instant rotatedAt) { this.rotatedAt = rotatedAt; }
    public String getRotatedBy() { return rotatedBy; }
    public void setRotatedBy(String rotatedBy) { this.rotatedBy = rotatedBy; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
}
