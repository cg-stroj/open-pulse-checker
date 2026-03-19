package io.openpulsechecker.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openpulsechecker.auth.AppUserEntity;
import io.openpulsechecker.auth.AppUserRepository;
import io.openpulsechecker.auth.UserRoleEntity;
import io.openpulsechecker.auth.UserRoleRepository;
import io.openpulsechecker.audit.AuditEventRepository;
import io.openpulsechecker.domain.CheckStatus;
import io.openpulsechecker.domain.IncidentState;
import io.openpulsechecker.persistence.CheckResultEntity;
import io.openpulsechecker.persistence.CheckResultRepository;
import io.openpulsechecker.persistence.IncidentEntity;
import io.openpulsechecker.persistence.IncidentRepository;
import io.openpulsechecker.persistence.MonitorEntity;
import io.openpulsechecker.persistence.MonitorRepository;
import io.openpulsechecker.persistence.StatusPageEntity;
import io.openpulsechecker.persistence.StatusPageMonitorEntity;
import io.openpulsechecker.persistence.StatusPageMonitorRepository;
import io.openpulsechecker.persistence.StatusPageRepository;
import io.openpulsechecker.service.HttpCheckClient;
import io.openpulsechecker.service.HttpCheckOutcome;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import io.openpulsechecker.support.H2TestDatabaseSupport;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "openpulse.security.bootstrap-admin.enabled=true",
        "openpulse.security.bootstrap-admin.username=admin",
        "openpulse.security.bootstrap-admin.password=admin-change-me"
})
class MonitorApiIntegrationTest extends H2TestDatabaseSupport {

    @Autowired private MockMvc mockMvc;
    @MockBean private HttpCheckClient httpCheckClient;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MonitorRepository monitorRepository;
    @Autowired private CheckResultRepository checkResultRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private StatusPageRepository statusPageRepository;
    @Autowired private StatusPageMonitorRepository statusPageMonitorRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @BeforeEach
    void ensureViewerUser() {
        if (appUserRepository.existsByUsername("viewer")) return;
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

    @Test
    void createAndRunCheckFlowAsAdmin() throws Exception {
        given(httpCheckClient.execute(anyString(), anyInt(), any(), any())).willReturn(new HttpCheckOutcome(true, 200, 50L, null));

        String createPayload = """
                {
                  "name": "Docs",
                  "type": "HTTP",
                  "targetUrl": "https://example.com",
                  "intervalSec": 60,
                  "enabled": true,
                  "timeoutMs": 1200,
                  "httpMethod": "POST",
                  "expectedResponseKeyword": "ready"
                }
                """;

        MvcResult created = mockMvc.perform(post("/api/v1/monitors")
                        .with(httpBasic("admin", "admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("HTTP"))
                .andExpect(jsonPath("$.httpMethod").value("POST"))
                .andExpect(jsonPath("$.expectedResponseKeyword").value("ready"))
                .andReturn();

        String response = created.getResponse().getContentAsString();
        String id = response.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/v1/monitors/" + id + "/run-check")
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitorId").value(id))
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/v1/monitors/" + id).with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastCheckAt").exists())
                .andExpect(jsonPath("$.lastCheckStatus").value("UP"));
    }

    @Test
    void updateMonitorAsAdmin() throws Exception {
        String createPayload = """
                {
                  "name": "Docs",
                  "type": "HTTP",
                  "targetUrl": "https://example.com",
                  "intervalSec": 60,
                  "enabled": true,
                  "timeoutMs": 1200
                }
                """;

        MvcResult created = mockMvc.perform(post("/api/v1/monitors")
                        .with(httpBasic("admin", "admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn();

        String response = created.getResponse().getContentAsString();
        String id = response.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(put("/api/v1/monitors/" + id)
                        .with(httpBasic("admin", "admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Docs API",
                                  "type": "HTTP",
                                  "targetUrl": "https://example.com/health",
                                  "intervalSec": 120,
                                  "enabled": false,
                                  "timeoutMs": 5000,
                                  "httpMethod": "PATCH",
                                  "expectedResponseKeyword": "healthy"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Docs API"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.intervalSec").value(120))
                .andExpect(jsonPath("$.httpMethod").value("PATCH"))
                .andExpect(jsonPath("$.expectedResponseKeyword").value("healthy"));
    }

    @Test
    void createAcceptsPingType() throws Exception {
        mockMvc.perform(post("/api/v1/monitors")
                        .with(httpBasic("admin", "admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Legacy Ping",
                                  "type": "PING",
                                  "targetUrl": "example.com",
                                  "intervalSec": 60,
                                  "enabled": true,
                                  "timeoutMs": 1200
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PING"));
    }

    @Test
    void writeEndpointsRequireAdminRole() throws Exception {
        String createPayload = """
                {
                  "name": "Docs",
                  "type": "HTTP",
                  "targetUrl": "https://example.com",
                  "intervalSec": 60,
                  "enabled": true,
                  "timeoutMs": 1200
                }
                """;

        mockMvc.perform(post("/api/v1/monitors").contentType(MediaType.APPLICATION_JSON).content(createPayload))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/monitors")
                        .with(httpBasic("viewer", "viewer-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    void readEndpointsAllowViewerRole() throws Exception {
        mockMvc.perform(get("/api/v1/monitors").with(httpBasic("viewer", "viewer-change-me")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/monitors/00000000-0000-0000-0000-000000000000/enabled")
                        .with(httpBasic("viewer", "viewer-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/monitors/00000000-0000-0000-0000-000000000000")
                        .with(httpBasic("viewer", "viewer-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"name\": \"Docs\",
                                  \"type\": \"HTTP\",
                                  \"targetUrl\": \"https://example.com\",
                                  \"intervalSec\": 60,
                                  \"enabled\": true,
                                  \"timeoutMs\": 1200
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void listSupportsPagingFilteringAndSorting() throws Exception {
        MonitorEntity alpha = new MonitorEntity();
        alpha.setName("Alpha API");
        alpha.setType(io.openpulsechecker.domain.MonitorType.HTTP);
        alpha.setTargetUrl("https://example.com/a");
        alpha.setIntervalSec(60);
        alpha.setEnabled(true);
        alpha.setTimeoutMs(1000);
        monitorRepository.save(alpha);

        MonitorEntity beta = new MonitorEntity();
        beta.setName("Beta API");
        beta.setType(io.openpulsechecker.domain.MonitorType.HTTP);
        beta.setTargetUrl("https://example.com/b");
        beta.setIntervalSec(30);
        beta.setEnabled(false);
        beta.setTimeoutMs(1000);
        monitorRepository.save(beta);

        mockMvc.perform(get("/api/v1/monitors")
                        .with(httpBasic("admin", "admin-change-me"))
                        .param("paged", "true")
                        .param("page", "0")
                        .param("size", "1")
                        .param("enabled", "true")
                        .param("q", "alpha")
                        .param("sortBy", "name")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items[0].name").value("Alpha API"));

        mockMvc.perform(get("/api/v1/monitors")
                        .with(httpBasic("admin", "admin-change-me"))
                        .param("paged", "true")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(25));
    }

    @Test
    void deleteMonitorAsAdminSucceedsAndWritesAudit() throws Exception {
        MonitorEntity monitor = new MonitorEntity();
        monitor.setName("Delete me");
        monitor.setType(io.openpulsechecker.domain.MonitorType.HTTP);
        monitor.setTargetUrl("https://example.com/delete");
        monitor.setIntervalSec(60);
        monitor.setEnabled(true);
        monitor.setTimeoutMs(1000);
        MonitorEntity savedMonitor = monitorRepository.save(monitor);

        StatusPageEntity statusPage = new StatusPageEntity();
        statusPage.setName("Ops");
        statusPage.setSlug("ops-delete");
        statusPage.setPublic(true);
        StatusPageEntity savedPage = statusPageRepository.save(statusPage);

        StatusPageMonitorEntity binding = new StatusPageMonitorEntity();
        binding.setStatusPageId(savedPage.getId());
        binding.setMonitorId(savedMonitor.getId());
        binding.setDisplayOrder(0);
        statusPageMonitorRepository.save(binding);

        mockMvc.perform(delete("/api/v1/monitors/" + savedMonitor.getId())
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertFalse(monitorRepository.existsById(savedMonitor.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(0, statusPageMonitorRepository.countByMonitorId(savedMonitor.getId()));
        org.junit.jupiter.api.Assertions.assertEquals("MONITOR_DELETE", auditEventRepository.findTopByOrderByOccurredAtDesc().getAction());
    }

    @Test
    void deleteMonitorForbiddenForViewer() throws Exception {
        mockMvc.perform(delete("/api/v1/monitors/00000000-0000-0000-0000-000000000001")
                        .with(httpBasic("viewer", "viewer-change-me")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteMonitorReturnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(delete("/api/v1/monitors/" + UUID.randomUUID())
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Monitor not found")));
    }

    @Test
    void deleteMonitorBlockedWhenHistoryExists() throws Exception {
        MonitorEntity monitor = new MonitorEntity();
        monitor.setName("Has history");
        monitor.setType(io.openpulsechecker.domain.MonitorType.HTTP);
        monitor.setTargetUrl("https://example.com/history");
        monitor.setIntervalSec(60);
        monitor.setEnabled(true);
        monitor.setTimeoutMs(1000);
        MonitorEntity savedMonitor = monitorRepository.save(monitor);

        CheckResultEntity checkResult = new CheckResultEntity();
        checkResult.setMonitorId(savedMonitor.getId());
        checkResult.setStatus(CheckStatus.DOWN);
        checkResult.setStatusCode(500);
        checkResult.setLatencyMs(33L);
        checkResult.setCheckedAt(Instant.now());
        checkResultRepository.save(checkResult);

        IncidentEntity incident = new IncidentEntity();
        incident.setMonitorId(savedMonitor.getId());
        incident.setState(IncidentState.OPEN);
        incident.setOpenedAt(Instant.now());
        incident.setReason("Down");
        incidentRepository.save(incident);

        mockMvc.perform(delete("/api/v1/monitors/" + savedMonitor.getId())
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Monitor deletion blocked")));

        org.junit.jupiter.api.Assertions.assertTrue(monitorRepository.existsById(savedMonitor.getId()));
    }
}

