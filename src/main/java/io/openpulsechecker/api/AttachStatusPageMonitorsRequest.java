package io.openpulsechecker.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record AttachStatusPageMonitorsRequest(@NotEmpty List<UUID> monitorIds) {
}
