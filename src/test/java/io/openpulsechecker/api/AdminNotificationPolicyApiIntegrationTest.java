package io.openpulsechecker.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "openpulse.security.bootstrap-admin.enabled=true",
        "openpulse.security.bootstrap-admin.username=admin",
        "openpulse.security.bootstrap-admin.password=admin-change-me"
})
class AdminNotificationPolicyApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setupViewer() {
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
    }

    @Test
    void adminEndpointsAreProtectedAndWritableByAdmin() throws Exception {
        String payload = """
                {
                  "scopeType":"GLOBAL",
                  "enabled":true,
                  "cooldownSeconds":120,
                  "dedupSeconds":60,
                  "routes":[
                    {"severity":"CRITICAL","webhookEnabled":true},
                    {"severity":"HIGH","webhookEnabled":true},
                    {"severity":"MEDIUM","webhookEnabled":true},
                    {"severity":"LOW","webhookEnabled":false},
                    {"severity":"INFO","webhookEnabled":false}
                  ],
                  "escalationSteps":[{"stepOrder":1,"afterSeconds":0,"minSeverity":"CRITICAL","webhookEnabled":true}]
                }
                """;

        mockMvc.perform(get("/api/v1/admin/notification-policies"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/notification-policies").with(httpBasic("viewer", "viewer-change-me")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/notification-policies")
                        .with(httpBasic("admin", "admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/notification-policies").with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk());
    }
}
