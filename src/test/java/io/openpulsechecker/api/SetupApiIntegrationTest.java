package io.openpulsechecker.api;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openpulsechecker.audit.AuditEventRepository;
import io.openpulsechecker.auth.AppUserRepository;
import io.openpulsechecker.auth.UserRoleRepository;
import io.openpulsechecker.setup.SetupStateEntity;
import io.openpulsechecker.setup.SetupStateRepository;
import io.openpulsechecker.setup.SetupTokenRepository;
import java.time.Instant;
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
        "openpulse.security.setup.token-ttl-seconds=1",
        "openpulse.rate-limit.sensitive.capacity=1000",
        "openpulse.rate-limit.sensitive.refill-tokens=1000",
        "openpulse.rate-limit.sensitive.refill-period-seconds=1"
})
class SetupApiIntegrationTest {

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
    void statusIssuesTokenAndFirstAdminLocksSetup() throws Exception {
        String statusPayload = mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true))
                .andExpect(jsonPath("$.setupLocked").value(false))
                .andExpect(jsonPath("$.setupToken", notNullValue()))
                .andExpect(jsonPath("$.setupTokenExpiresAt", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode statusJson = objectMapper.readTree(statusPayload);
        String token = statusJson.get("setupToken").asText();

        String createPayload = """
                {"username":"owner","password":"owner-change-me","setupToken":"%s"}
                """.formatted(token);
        mockMvc.perform(post("/api/v1/setup/first-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(false))
                .andExpect(jsonPath("$.setupLocked").value(true))
                .andExpect(jsonPath("$.setupToken", nullValue()))
                .andExpect(jsonPath("$.setupTokenExpiresAt", nullValue()));

        mockMvc.perform(post("/api/v1/setup/first-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isConflict());
    }

    @Test
    void tokenExpirationAndRateLimitAreEnforced() throws Exception {
        String statusPayload = mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(statusPayload).get("setupToken").asText();

        Thread.sleep(1200);

        String createPayload = """
                {"username":"owner2","password":"owner2-change-me","setupToken":"%s"}
                """.formatted(token);
        mockMvc.perform(post("/api/v1/setup/first-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid or expired setup token"));

        mockMvc.perform(get("/api/v1/setup/status")).andExpect(status().isOk());
    }

    @Test
    void weakPasswordIsRejectedByValidation() throws Exception {
        String statusPayload = mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(statusPayload).get("setupToken").asText();

        String createPayload = """
                {"username":"owner4","password":"weakpass","setupToken":"%s"}
                """.formatted(token);

        mockMvc.perform(post("/api/v1/setup/first-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("password")));
    }

    @Test
    void setupActionsAreAudited() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status")).andExpect(status().isOk());

        String badPayload = """
                {"username":"owner3","password":"owner3-change-me","setupToken":"invalid-token"}
                """;
        mockMvc.perform(post("/api/v1/setup/first-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isBadRequest());

        long tokenIssueCount = auditEventRepository.findAll().stream()
                .filter(e -> "SETUP_TOKEN_ISSUE".equals(e.getAction()) && "SUCCESS".equals(e.getResult()))
                .count();
        long failedCreateCount = auditEventRepository.findAll().stream()
                .filter(e -> "SETUP_FIRST_ADMIN".equals(e.getAction()) && "FAILURE".equals(e.getResult()))
                .count();

        org.junit.jupiter.api.Assertions.assertTrue(tokenIssueCount >= 1);
        org.junit.jupiter.api.Assertions.assertTrue(failedCreateCount >= 1);
    }
}
