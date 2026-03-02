package io.openpulsechecker.api;

import io.openpulsechecker.domain.HttpMethod;
import io.openpulsechecker.domain.MonitorType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateMonitorRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull MonitorType type,
        @NotBlank @Size(max = 1024) String targetUrl,
        @Min(10) @Max(86400) int intervalSec,
        @NotNull Boolean enabled,
        @Min(100) @Max(120000) int timeoutMs,
        HttpMethod httpMethod,
        @Size(max = 255) String expectedResponseKeyword
) {
}
