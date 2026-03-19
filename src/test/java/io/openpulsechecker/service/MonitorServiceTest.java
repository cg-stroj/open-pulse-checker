package io.openpulsechecker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.openpulsechecker.api.CreateMonitorRequest;
import io.openpulsechecker.api.MonitorResponse;
import io.openpulsechecker.audit.AuditService;
import io.openpulsechecker.domain.HttpMethod;
import io.openpulsechecker.domain.MonitorType;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.IncidentRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import io.openpulsechecker.persistence.StatusPageMonitorRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private MonitorRepository monitorRepository;
    @Mock
    private CheckResultRepository checkResultRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private StatusPageMonitorRepository statusPageMonitorRepository;
    @Mock
    private AuditService auditService;

    @Test
    void createSupportsPingType() {
        MonitorService service = new MonitorService(
                monitorRepository,
                checkResultRepository,
                incidentRepository,
                statusPageMonitorRepository,
                auditService);

        when(monitorRepository.save(any(MonitorEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateMonitorRequest request = new CreateMonitorRequest(
                "Ping",
                MonitorType.PING,
                "example.com",
                60,
                true,
                1000,
                null,
                null);

        MonitorResponse response = service.create(request);
        assertEquals(MonitorType.PING, response.type());
    }

    @Test
    void listPreservesPingTypeInApiResponse() {
        MonitorEntity entity = new MonitorEntity();
        entity.setName("Legacy ping");
        entity.setType(MonitorType.PING);
        entity.setTargetUrl("example.com");
        entity.setIntervalSec(60);
        entity.setEnabled(true);
        entity.setTimeoutMs(1000);

        when(monitorRepository.findAll()).thenReturn(List.of(entity));
        when(checkResultRepository.findLatestForMonitorIds(any())).thenReturn(List.of());

        MonitorService service = new MonitorService(
                monitorRepository,
                checkResultRepository,
                incidentRepository,
                statusPageMonitorRepository,
                auditService);

        MonitorResponse response = service.listAll().get(0);
        assertEquals(MonitorType.PING, response.type());
    }

    @Test
    void createPersistsHttpFieldsForHttpMonitor() {
        MonitorService service = new MonitorService(
                monitorRepository,
                checkResultRepository,
                incidentRepository,
                statusPageMonitorRepository,
                auditService);

        when(monitorRepository.save(any(MonitorEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateMonitorRequest request = new CreateMonitorRequest(
                "API",
                MonitorType.HTTP,
                "https://example.com/health",
                30,
                true,
                1500,
                HttpMethod.PATCH,
                "  ok  ");

        MonitorResponse response = service.create(request);
        assertEquals(HttpMethod.PATCH, response.httpMethod());
        assertEquals("ok", response.expectedResponseKeyword());
    }
}
