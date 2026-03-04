package io.openpulsechecker.api;

import io.openpulsechecker.auth.AppUserEntity;
import io.openpulsechecker.auth.AppUserRepository;
import io.openpulsechecker.auth.UserRoleEntity;
import io.openpulsechecker.auth.UserRoleRepository;
import io.openpulsechecker.support.H2TestDatabaseSupport;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "openpulse.security.bootstrap-admin.enabled=true",
        "openpulse.security.bootstrap-admin.username=admin",
        "openpulse.security.bootstrap-admin.password=admin-change-me",
        "management.endpoints.web.exposure.include=health,info,metrics"
})
class ActuatorMetricsIntegrationTest extends H2TestDatabaseSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void ensureViewerUser() {
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
    void metricsRequireAdminRoleAndExpectedDashboardMetricsAreExposed() throws Exception {
        mockMvc.perform(get("/actuator/metrics/openpulse.scheduler.lock.acquire.success"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/metrics/openpulse.scheduler.lock.acquire.success")
                        .with(httpBasic("viewer", "viewer-change-me")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics")
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray())
                .andExpect(jsonPath("$.names[?(@ == 'openpulse.alerts.dispatch.latency')]").exists())
                .andExpect(jsonPath("$.names[?(@ == 'openpulse.alerts.delivery.delay')]").exists());

        mockMvc.perform(get("/actuator/metrics/openpulse.alerts.dispatch.latency")
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("openpulse.alerts.dispatch.latency"));
    }

    @Test
    void unknownMetricReturnsNotFoundForActionableMisconfigurationDiagnostics() throws Exception {
        mockMvc.perform(get("/actuator/metrics/openpulse.nonexistent.metric")
                        .with(httpBasic("admin", "admin-change-me")))
                .andExpect(status().isNotFound());
    }
}
