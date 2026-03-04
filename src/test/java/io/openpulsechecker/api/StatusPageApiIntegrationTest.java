package io.openpulsechecker.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openpulsechecker.auth.AppUserEntity;
import io.openpulsechecker.auth.AppUserRepository;
import io.openpulsechecker.auth.UserRoleEntity;
import io.openpulsechecker.auth.UserRoleRepository;
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
import io.openpulsechecker.support.H2TestDatabaseSupport;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "openpulse.security.bootstrap-admin.enabled=true",
        "openpulse.security.bootstrap-admin.username=admin",
        "openpulse.security.bootstrap-admin.password=admin-change-me"
})
class StatusPageApiIntegrationTest extends H2TestDatabaseSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StatusPageRepository statusPageRepository;
    @Autowired private StatusPageMonitorRepository statusPageMonitorRepository;
    @Autowired private MonitorRepository monitorRepository;
    @MockBean private HttpCheckClient httpCheckClient;

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
        given(httpCheckClient.execute(anyString(), anyInt(), any(), any())).willReturn(new HttpCheckOutcome(true, 200, 25L, null));
    }

    @Test
    void publicPageFetchReturnsMetadataMonitorsAndTimeline() throws Exception {
        MonitorEntity monitor = new MonitorEntity();
        monitor.setName("API");
        monitor.setType(io.openpulsechecker.domain.MonitorType.HTTP);
        monitor.setTargetUrl("https://example.com/api");
        monitor.setIntervalSec(60);
        monitor.setEnabled(true);
        monitor.setTimeoutMs(1000);
        MonitorEntity savedMonitor = monitorRepository.save(monitor);

        StatusPageEntity page = new StatusPageEntity();
        page.setName("Main Status");
        page.setSlug("main-status");
        page.setPublic(true);
        StatusPageEntity savedPage = statusPageRepository.save(page);

        StatusPageMonitorEntity link = new StatusPageMonitorEntity();
        link.setStatusPageId(savedPage.getId());
        link.setMonitorId(savedMonitor.getId());
        link.setDisplayOrder(0);
        statusPageMonitorRepository.save(link);

        mockMvc.perform(post("/api/v1/monitors/" + savedMonitor.getId() + "/run-check")
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/status-pages/main-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.slug").value("main-status"))
                .andExpect(jsonPath("$.overallStatus").value("OPERATIONAL"))
                .andExpect(jsonPath("$.monitors[0].monitorId").value(savedMonitor.getId().toString()));
    }

    @Test
    void nonPublicPageReturnsNotFoundOnPublicEndpoint() throws Exception {
        StatusPageEntity page = new StatusPageEntity();
        page.setName("Private");
        page.setSlug("private-status");
        page.setPublic(false);
        statusPageRepository.save(page);

        mockMvc.perform(get("/api/v1/public/status-pages/private-status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminManageEndpointsRequireAdminRole() throws Exception {
        String payload = """
                {
                  "name":"Main",
                  "slug":"main",
                  "isPublic":true
                }
                """;

        mockMvc.perform(post("/api/v1/status-pages").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/status-pages")
                        .with(httpBasic("viewer", "viewer-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());

        String response = mockMvc.perform(post("/api/v1/status-pages")
                        .with(httpBasic("admin", "admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String pageId = response.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/api/v1/status-pages").with(httpBasic("viewer", "viewer-change-me")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/status-pages").with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk());

        MonitorEntity monitor = new MonitorEntity();
        monitor.setName("API");
        monitor.setType(io.openpulsechecker.domain.MonitorType.HTTP);
        monitor.setTargetUrl("https://example.com");
        monitor.setIntervalSec(60);
        monitor.setEnabled(true);
        monitor.setTimeoutMs(1000);
        UUID monitorId = monitorRepository.save(monitor).getId();

        mockMvc.perform(post("/api/v1/status-pages/" + pageId + "/monitors")
                        .with(httpBasic("viewer", "viewer-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monitorIds\":[\"" + monitorId + "\"]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/status-pages/" + pageId + "/monitors/" + monitorId)
                        .with(httpBasic("viewer", "viewer-change-me")))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusPageListSupportsPagingFilteringAndSorting() throws Exception {
        statusPageRepository.deleteAll();

        StatusPageEntity publicPage = new StatusPageEntity();
        publicPage.setName("Main Status");
        publicPage.setSlug("ops-main-status");
        publicPage.setPublic(true);
        statusPageRepository.save(publicPage);

        StatusPageEntity privatePage = new StatusPageEntity();
        privatePage.setName("Internal Status");
        privatePage.setSlug("internal-status");
        privatePage.setPublic(false);
        statusPageRepository.save(privatePage);

        mockMvc.perform(get("/api/v1/status-pages")
                        .with(httpBasic("admin", "admin-change-me"))
                        .param("paged", "true")
                        .param("size", "1")
                        .param("isPublic", "true")
                        .param("q", "main")
                        .param("sortBy", "name")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("ops-main-status"));
    }
}
