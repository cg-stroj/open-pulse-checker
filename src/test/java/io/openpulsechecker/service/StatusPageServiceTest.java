package io.openpulsechecker.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openpulsechecker.api.PublicStatusPageResponse;
import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.domain.StatusPageOverallStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatusPageServiceTest {

    private final StatusPageService service = new StatusPageService(null, null, null, null, null, null, null);

    @Test
    void deriveStatusOperationalWhenAllUp() {
        List<PublicStatusPageResponse.PublicMonitorSummary> summaries = List.of(
                summary(CheckStatus.UP),
                summary(CheckStatus.UP)
        );

        assertThat(service.deriveOverallStatus(summaries)).isEqualTo(StatusPageOverallStatus.OPERATIONAL);
    }

    @Test
    void deriveStatusOutageWhenAtLeastOneDown() {
        List<PublicStatusPageResponse.PublicMonitorSummary> summaries = List.of(
                summary(CheckStatus.UP),
                summary(CheckStatus.DOWN)
        );

        assertThat(service.deriveOverallStatus(summaries)).isEqualTo(StatusPageOverallStatus.OUTAGE);
    }

    @Test
    void deriveStatusDegradedWhenUnknownAndNoDown() {
        List<PublicStatusPageResponse.PublicMonitorSummary> summaries = List.of(
                summary(CheckStatus.UNKNOWN),
                summary(CheckStatus.UP)
        );

        assertThat(service.deriveOverallStatus(summaries)).isEqualTo(StatusPageOverallStatus.DEGRADED);
    }

    private PublicStatusPageResponse.PublicMonitorSummary summary(CheckStatus status) {
        return new PublicStatusPageResponse.PublicMonitorSummary(UUID.randomUUID(), "m", 0, null, status, null, null, null);
    }
}
