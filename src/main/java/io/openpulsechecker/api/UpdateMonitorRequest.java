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
        @Min(value = 60, message = "must be between 60 and 300 seconds")
        @Max(value = 300, message = "must be between 60 and 300 seconds") int intervalSec,
        @NotNull Boolean enabled,
        @Min(100) @Max(120000) int timeoutMs,
        HttpMethod httpMethod,
        @Size(max = 255) String expectedResponseKeyword,
        Boolean emailAlertOnDown,
        Boolean emailAlertOnRecovery
) {
}
