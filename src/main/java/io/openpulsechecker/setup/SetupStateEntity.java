package io.openpulsechecker.setup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "setup_state")
public class SetupStateEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "setup_locked", nullable = false)
    private boolean setupLocked;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public boolean isSetupLocked() {
        return setupLocked;
    }

    public void setSetupLocked(boolean setupLocked) {
        this.setupLocked = setupLocked;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
