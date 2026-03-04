package io.openpulsechecker.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openpulsechecker.audit.AuditService;
import io.openpulsechecker.auth.AppUserEntity;
import io.openpulsechecker.auth.AppUserRepository;
import io.openpulsechecker.auth.UserRoleEntity;
import io.openpulsechecker.auth.UserRoleRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class AdminAuditApiIntegrationTest extends H2TestDatabaseSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuditService auditService;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setupViewerAndSeedAudit() {
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

        auditService.log("admin", "INCIDENT_RESOLVE", "incident/123", "SUCCESS", "Resolved via API integration test");
    }

    @Test
    void listAndExportRequireAdminAndReturnData() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-events"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/audit-events").with(httpBasic("viewer", "viewer-change-me")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .with(httpBasic("admin", "admin-change-me"))
                        .queryParam("action", "INCIDENT_RESOLVE")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(10)))
                .andExpect(jsonPath("$.items[0].action", is("INCIDENT_RESOLVE")));

        mockMvc.perform(get("/api/v1/admin/audit-events/export")
                        .with(httpBasic("admin", "admin-change-me"))
                        .queryParam("format", "csv")
                        .queryParam("action", "INCIDENT_RESOLVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INCIDENT_RESOLVE")));

        mockMvc.perform(get("/api/v1/admin/audit-events/export")
                        .with(httpBasic("admin", "admin-change-me"))
                        .queryParam("format", "json")
                        .queryParam("action", "INCIDENT_RESOLVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"action\":\"INCIDENT_RESOLVE\"")));
    }
}
