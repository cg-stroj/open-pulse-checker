package io.openpulsechecker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openpulsechecker.audit.AuditService;
import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.domain.MonitorType;
import io.openpulsechecker.persistence.CheckResultEntity;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckExecutionServiceTest {

    @Mock
    private MonitorService monitorService;
    @Mock
    private HttpCheckClient httpCheckClient;
    @Mock
    private CheckResultRepository checkResultRepository;
    @Mock
    private IncidentService incidentService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private CheckExecutionService checkExecutionService;

    @Test
    void persistsDownResultFromHttpOutcome() {
        UUID monitorId = UUID.randomUUID();
        MonitorEntity monitor = new MonitorEntity();
        monitor.setId(monitorId);
        monitor.setType(MonitorType.HTTP);
        monitor.setTargetUrl("https://example.com");
        monitor.setTimeoutMs(500);

        when(monitorService.getEntity(monitorId)).thenReturn(monitor);
        when(httpCheckClient.execute("https://example.com", 500))
                .thenReturn(new HttpCheckOutcome(false, 503, 120L, null));
        when(checkResultRepository.save(any(CheckResultEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        checkExecutionService.runCheck(monitorId);

        ArgumentCaptor<CheckResultEntity> captor = ArgumentCaptor.forClass(CheckResultEntity.class);
        verify(checkResultRepository).save(captor.capture());
        assertEquals(CheckStatus.DOWN, captor.getValue().getStatus());
        assertEquals(503, captor.getValue().getStatusCode());
        verify(incidentService).applyTransition(any(), any(), any(), any());
    }
}
