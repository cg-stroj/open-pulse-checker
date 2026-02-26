package io.openpulsechecker.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class StatusPageMonitorId implements Serializable {
    private UUID statusPageId;
    private UUID monitorId;

    public StatusPageMonitorId() {
    }

    public StatusPageMonitorId(UUID statusPageId, UUID monitorId) {
        this.statusPageId = statusPageId;
        this.monitorId = monitorId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StatusPageMonitorId that)) return false;
        return Objects.equals(statusPageId, that.statusPageId) && Objects.equals(monitorId, that.monitorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusPageId, monitorId);
    }
}
