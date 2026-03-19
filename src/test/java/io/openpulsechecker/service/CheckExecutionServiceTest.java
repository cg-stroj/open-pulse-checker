package io.openpulsechecker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openpulsechecker.audit.AuditService;
import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.domain.HttpMethod;
import io.openpulsechecker.domain.MonitorType;
import io.openpulsechecker.persistence.CheckResultEntity;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckExecutionServiceTest {

    @Mock
    private MonitorService monitorService;
    @Mock
    private HttpCheckClient httpCheckClient;
    @Mock
    private NetworkCheckClient networkCheckClient;
    @Mock
    private CheckResultRepository checkResultRepository;
    @Mock
    private IncidentService incidentService;
    @Mock
    private AuditService auditService;

    @Test
    void persistsDownResultFromHttpOutcomeWithConfiguredMethodAndKeyword() {
        UUID monitorId = UUID.randomUUID();
        MonitorEntity monitor = new MonitorEntity();
        monitor.setId(monitorId);
        monitor.setType(MonitorType.HTTP);
        monitor.setTargetUrl("https://example.com");
        monitor.setTimeoutMs(500);
        monitor.setHttpMethod(io.openpulsechecker.domain.HttpMethod.PATCH);
        monitor.setExpectedResponseKeyword("healthy");

        when(monitorService.getEntity(monitorId)).thenReturn(monitor);
        when(httpCheckClient.execute("https://example.com", 500, HttpMethod.PATCH, "healthy"))
                .thenReturn(new HttpCheckOutcome(false, 503, 120L, null));
        when(checkResultRepository.save(any(CheckResultEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CheckExecutionService checkExecutionService = new CheckExecutionService(
                monitorService,
                httpCheckClient,
                networkCheckClient,
                checkResultRepository,
                incidentService,
                auditService,
                meterRegistry);

        checkExecutionService.runCheck(monitorId);

        verify(httpCheckClient).execute("https://example.com", 500, HttpMethod.PATCH, "healthy");

        ArgumentCaptor<CheckResultEntity> captor = ArgumentCaptor.forClass(CheckResultEntity.class);
        verify(checkResultRepository).save(captor.capture());
        assertEquals(CheckStatus.DOWN, captor.getValue().getStatus());
        assertEquals(503, captor.getValue().getStatusCode());
        verify(incidentService).applyTransition(any(), any(), any(), any());
        assertEquals(1L, meterRegistry.find("openpulse.checks.latency").timers().size());
    }

    @Test
    void persistsUpResultForTcpOutcome() {
        UUID monitorId = UUID.randomUUID();
        MonitorEntity monitor = new MonitorEntity();
        monitor.setId(monitorId);
        monitor.setType(MonitorType.TCP);
        monitor.setTargetUrl("localhost:5432");
        monitor.setTimeoutMs(500);

        when(monitorService.getEntity(monitorId)).thenReturn(monitor);
        when(networkCheckClient.executeTcp("localhost:5432", 500))
                .thenReturn(new HttpCheckOutcome(true, null, 20L, null));
        when(checkResultRepository.save(any(CheckResultEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckExecutionService checkExecutionService = new CheckExecutionService(
                monitorService,
                httpCheckClient,
                networkCheckClient,
                checkResultRepository,
                incidentService,
                auditService,
                new SimpleMeterRegistry());

        checkExecutionService.runCheck(monitorId);

        ArgumentCaptor<CheckResultEntity> captor = ArgumentCaptor.forClass(CheckResultEntity.class);
        verify(checkResultRepository).save(captor.capture());
        assertEquals(CheckStatus.UP, captor.getValue().getStatus());
        assertNull(captor.getValue().getStatusCode());
        assertEquals(20L, captor.getValue().getLatencyMs());
    }

    @Test
    void persistsDownResultForLegacyPingOutcome() {
        UUID monitorId = UUID.randomUUID();
        MonitorEntity monitor = new MonitorEntity();
        monitor.setId(monitorId);
        monitor.setType(MonitorType.PING);
        monitor.setTargetUrl("https://example.com");
        monitor.setTimeoutMs(500);

        when(monitorService.getEntity(monitorId)).thenReturn(monitor);
        when(networkCheckClient.executePing("https://example.com", 500))
                .thenReturn(new HttpCheckOutcome(false, null, 42L, "Host unreachable"));
        when(checkResultRepository.save(any(CheckResultEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckExecutionService checkExecutionService = new CheckExecutionService(
                monitorService,
                httpCheckClient,
                networkCheckClient,
                checkResultRepository,
                incidentService,
                auditService,
                new SimpleMeterRegistry());

        checkExecutionService.runCheck(monitorId);

        ArgumentCaptor<CheckResultEntity> captor = ArgumentCaptor.forClass(CheckResultEntity.class);
        verify(checkResultRepository).save(captor.capture());
        assertEquals(CheckStatus.DOWN, captor.getValue().getStatus());
        assertNull(captor.getValue().getStatusCode());
        assertEquals("Host unreachable", captor.getValue().getError());
    }
}
