package io.openpulsechecker.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
@IdClass(UserRoleEntity.Key.class)
public class UserRoleEntity {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Id
    @Column(name = "role_name", nullable = false, length = 40)
    private String roleName;
    @Column(nullable = false)
    private Instant createdAt;

    public static class Key implements Serializable {
        public UUID userId;
        public String roleName;
        public Key() {}
        public Key(UUID userId, String roleName) { this.userId = userId; this.roleName = roleName; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof Key key)) return false; return Objects.equals(userId, key.userId) && Objects.equals(roleName, key.roleName); }
        @Override public int hashCode() { return Objects.hash(userId, roleName); }
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
