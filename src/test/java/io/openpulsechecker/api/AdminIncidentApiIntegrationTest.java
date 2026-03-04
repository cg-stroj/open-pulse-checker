package io.openpulsechecker.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openpulsechecker.audit.AuditEventEntity;
import io.openpulsechecker.audit.AuditEventRepository;
import io.openpulsechecker.auth.AppUserEntity;
import io.openpulsechecker.auth.AppUserRepository;
import io.openpulsechecker.auth.UserRoleEntity;
import io.openpulsechecker.auth.UserRoleRepository;
import io.openpulsechecker.domain.IncidentState;
import io.openpulsechecker.domain.MonitorType;
import io.openpulsechecker.incident.IncidentManualAction;
import io.openpulsechecker.incident.IncidentManualEventEntity;
import io.openpulsechecker.incident.IncidentManualEventRepository;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.IncidentEntity;
import io.openpulsechecker.persistence.IncidentRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import io.openpulsechecker.support.H2TestDatabaseSupport;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "openpulse.security.bootstrap-admin.enabled=true",
        "openpulse.security.bootstrap-admin.username=admin",
        "openpulse.security.bootstrap-admin.password=admin-change-me"
})
class AdminIncidentApiIntegrationTest extends H2TestDatabaseSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MonitorRepository monitorRepository;
    @Autowired private CheckResultRepository checkResultRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private IncidentManualEventRepository incidentManualEventRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    private UUID incidentId;

    @BeforeEach
    void setup() {
        if (!appUserRepository.existsByUsername("viewer")) {
            AppUserEntity viewer = new AppUserEntity();
            viewer.setUsername("viewer");
            viewer.setPasswordHash(passwordEncoder.encode("viewer-change-me"));
            viewer.setEnabled(true);
            AppUserEntity saved = appUserRepository.save(viewer);

            UserRoleEntity role = new UserRoleEntity();
            role.setUserId(saved.getId());
            role.setRoleName("VIEWER");
            role.setCreatedAt(Instant.now());
            userRoleRepository.save(role);
        }

        incidentManualEventRepository.deleteAll();
        incidentRepository.deleteAll();
        checkResultRepository.deleteAll();
        monitorRepository.deleteAll();

        MonitorEntity monitor = new MonitorEntity();
        monitor.setName("api");
        monitor.setType(MonitorType.HTTP);
        monitor.setTargetUrl("https://example.com/health");
        monitor.setEnabled(true);
        monitor.setIntervalSec(60);
        monitor.setTimeoutMs(5000);
        MonitorEntity savedMonitor = monitorRepository.save(monitor);

        IncidentEntity incident = new IncidentEntity();
        incident.setMonitorId(savedMonitor.getId());
        incident.setState(IncidentState.OPEN);
        incident.setOpenedAt(Instant.parse("2026-02-27T10:00:00Z"));
        incident.setReason("HTTP 500");
        incidentId = incidentRepository.save(incident).getId();
    }

    @Test
    void adminEndpointsRequireAdminAndPersistAuditTrail() throws Exception {
        String payload = "{" +
                "\"reason\":\"on call acknowledged\"" +
                "}";

        mockMvc.perform(get("/api/v1/admin/incidents"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/incidents")
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(incidentId.toString()))
                .andExpect(jsonPath("$[0].monitorName").value("api"));

        mockMvc.perform(post("/api/v1/admin/incidents/{id}/acknowledge", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/incidents/{id}/acknowledge", incidentId)
                        .with(httpBasic("viewer", "viewer-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/incidents/{id}/acknowledge", incidentId)
                        .with(httpBasic("admin", "admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACKNOWLEDGED"));

        IncidentManualEventEntity event = incidentManualEventRepository.findByIncidentIdOrderByOccurredAtAsc(incidentId).getFirst();
        Assertions.assertEquals(IncidentManualAction.ACKNOWLEDGED, event.getAction());
        Assertions.assertEquals("admin", event.getActor());

        AuditEventEntity audit = auditEventRepository.findTopByOrderByOccurredAtDesc();
        Assertions.assertEquals("incident.acknowledge", audit.getAction());
        Assertions.assertEquals("admin", audit.getUsername());

        mockMvc.perform(post("/api/v1/admin/incidents/{id}/resolve", incidentId)
                        .with(httpBasic("admin", "admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"mitigated manually\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RESOLVED"));

        mockMvc.perform(post("/api/v1/admin/incidents/{id}/reopen", incidentId)
                        .with(httpBasic("admin", "admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"regression observed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OPEN"));

        mockMvc.perform(post("/api/v1/admin/incidents/{id}/annotations", incidentId)
                        .with(httpBasic("admin", "admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"investigation note\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/incidents/{id}/events", incidentId)
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$[1].action").value("RESOLVED_MANUALLY"))
                .andExpect(jsonPath("$[2].action").value("REOPENED"))
                .andExpect(jsonPath("$[3].action").value("ANNOTATION_ADDED"));
    }

    @Test
    void incidentListSupportsPagingFilteringAndDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/admin/incidents")
                        .with(httpBasic("admin", "admin-change-me"))
                        .param("paged", "true")
                        .param("page", "0")
                        .param("size", "1")
                        .param("state", "OPEN")
                        .param("q", "HTTP")
                        .param("sortBy", "openedAt")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(incidentId.toString()));

        mockMvc.perform(get("/api/v1/admin/incidents")
                        .with(httpBasic("admin", "admin-change-me"))
                        .param("paged", "true")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(25));
    }
}
