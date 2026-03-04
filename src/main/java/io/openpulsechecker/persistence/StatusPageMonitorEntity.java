package io.openpulsechecker.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "status_page_monitors")
@IdClass(StatusPageMonitorId.class)
public class StatusPageMonitorEntity {

    @Id
    @Column(nullable = false)
    private UUID statusPageId;

    @Id
    @Column(nullable = false)
    private UUID monitorId;

    @Column(nullable = false)
    private int displayOrder;

    @Column
    private UUID componentGroupId;

    public UUID getStatusPageId() { return statusPageId; }
    public void setStatusPageId(UUID statusPageId) { this.statusPageId = statusPageId; }
    public UUID getMonitorId() { return monitorId; }
    public void setMonitorId(UUID monitorId) { this.monitorId = monitorId; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public UUID getComponentGroupId() { return componentGroupId; }
    public void setComponentGroupId(UUID componentGroupId) { this.componentGroupId = componentGroupId; }
}
