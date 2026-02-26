package io.openpulsechecker.api;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openpulsechecker.auth.AppUserEntity;
import io.openpulsechecker.auth.AppUserRepository;
import io.openpulsechecker.auth.UserRoleEntity;
import io.openpulsechecker.auth.UserRoleRepository;
import io.openpulsechecker.service.HttpCheckClient;
import io.openpulsechecker.service.HttpCheckOutcome;
import java.time.Instant;
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

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "openpulse.security.bootstrap-admin.enabled=true",
        "openpulse.security.bootstrap-admin.username=admin",
        "openpulse.security.bootstrap-admin.password=admin-change-me"
})
class MonitorApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private HttpCheckClient httpCheckClient;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

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
        given(httpCheckClient.execute(anyString(), anyInt())).willReturn(new HttpCheckOutcome(true, 200, 50L, null));

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
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("HTTP"))
                .andReturn();

        String response = created.getResponse().getContentAsString();
        String id = response.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/v1/monitors/" + id + "/run-check")
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitorId").value(id))
                .andExpect(jsonPath("$.status").value("UP"));
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
    }
}
