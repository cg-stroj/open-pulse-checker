package io.openpulsechecker.api;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openpulsechecker.audit.AuditEventRepository;
import io.openpulsechecker.auth.AppUserRepository;
import io.openpulsechecker.auth.UserRoleRepository;
import io.openpulsechecker.setup.SetupStateEntity;
import io.openpulsechecker.setup.SetupStateRepository;
import io.openpulsechecker.setup.SetupTokenRepository;
import io.openpulsechecker.support.H2TestDatabaseSupport;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "openpulse.security.bootstrap-admin.enabled=false",
        "openpulse.security.setup.bootstrap-protection-enabled=true",
        "openpulse.security.setup.bootstrap-secret=test-bootstrap-secret",
        "openpulse.security.setup.bootstrap-allowed-cidrs=203.0.113.0/24",
        "openpulse.rate-limit.sensitive.capacity=1000",
        "openpulse.rate-limit.sensitive.refill-tokens=1000",
        "openpulse.rate-limit.sensitive.refill-period-seconds=1"
})
class SetupBootstrapProtectionIntegrationTest extends H2TestDatabaseSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private SetupTokenRepository setupTokenRepository;
    @Autowired private SetupStateRepository setupStateRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @BeforeEach
    void resetState() {
        userRoleRepository.deleteAll();
        appUserRepository.deleteAll();
        setupTokenRepository.deleteAll();
        auditEventRepository.deleteAll();

        SetupStateEntity state = setupStateRepository.findById(1).orElseGet(() -> {
            SetupStateEntity created = new SetupStateEntity();
            created.setId(1);
            return created;
        });
        state.setSetupLocked(false);
        state.setUpdatedAt(Instant.now());
        setupStateRepository.save(state);
    }

    @Test
    void setupStatusDeniedWithoutSecretOrAllowlist() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("setup bootstrap access denied"));

        long deniedEvents = auditEventRepository.findAll().stream()
                .filter(e -> "SETUP_BOOTSTRAP_DENIED".equals(e.getAction()) && "FAILURE".equals(e.getResult()))
                .count();
        Assertions.assertEquals(1, deniedEvents);
    }

    @Test
    void setupStatusAllowedWithBootstrapSecretHeader() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status")
                        .header("X-Setup-Bootstrap-Secret", "test-bootstrap-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupToken", notNullValue()));
    }

    @Test
    void firstAdminAllowedFromAllowlistedNetworkWithoutSecret() throws Exception {
        String statusPayload = mockMvc.perform(get("/api/v1/setup/status")
                        .header("X-Forwarded-For", "203.0.113.19"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(statusPayload).get("setupToken").asText();
        String createPayload = """
                {"username":"owner","password":"owner-change-me","setupToken":"%s"}
                """.formatted(token);

        mockMvc.perform(post("/api/v1/setup/first-admin")
                        .header("X-Forwarded-For", "203.0.113.19")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("owner"));
    }
}
