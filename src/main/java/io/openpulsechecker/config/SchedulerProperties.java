package io.openpulsechecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpulse.scheduler")
public record SchedulerProperties(
        int pollIntervalMs,
        int workerPoolSize
) {
}
