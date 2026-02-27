package io.openpulsechecker.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidentActionRequest(
        @NotBlank @Size(max = 2048) String reason
) {}
