package io.openpulsechecker.maintenance;

import java.util.UUID;

public record MaintenanceEvaluation(
        boolean active,
        MaintenancePolicy policy,
        UUID windowId,
        String windowName,
        String annotation
) {
    public static MaintenanceEvaluation inactive() {
        return new MaintenanceEvaluation(false, null, null, null, null);
    }
}
