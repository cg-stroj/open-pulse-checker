package io.openpulsechecker.alerting;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchedAlertRepository extends JpaRepository<DispatchedAlertEntity, String> {
    long countByMonitorIdAndIncidentIdAndSeverityAndChannelAndCreatedAtGreaterThanEqual(
            UUID monitorId, UUID incidentId, String severity, String channel, Instant createdAt);

    long countByMonitorIdAndSeverityAndChannelAndCreatedAtGreaterThanEqual(
            UUID monitorId, String severity, String channel, Instant createdAt);
}
