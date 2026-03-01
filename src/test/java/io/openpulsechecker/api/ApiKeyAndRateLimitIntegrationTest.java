package io.openpulsechecker.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openpulsechecker.apikey.ApiKeyHasher;
import io.openpulsechecker.apikey.ServiceApiKeyEntity;
import io.openpulsechecker.apikey.ServiceApiKeyRepository;
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
        "openpulse.rate-limit.sensitive.capacity=2",
        "openpulse.rate-limit.sensitive.refill-tokens=2",
        "openpulse.rate-limit.sensitive.refill-period-seconds=1"
})
class ApiKeyAndRateLimitIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ServiceApiKeyRepository repository;
    @Autowired ApiKeyHasher hasher;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        ServiceApiKeyEntity admin = new ServiceApiKeyEntity();
        admin.setKeyId("svc-admin");
        admin.setSecretHash(hasher.hash("secret-admin"));
        admin.setRoleName("ADMIN");
        admin.setEnabled(true);
        repository.save(admin);

        ServiceApiKeyEntity viewer = new ServiceApiKeyEntity();
        viewer.setKeyId("svc-viewer");
        viewer.setSecretHash(hasher.hash("secret-viewer"));
        viewer.setRoleName("VIEWER");
        viewer.setEnabled(true);
        repository.save(viewer);

        ServiceApiKeyEntity disabled = new ServiceApiKeyEntity();
        disabled.setKeyId("svc-disabled");
        disabled.setSecretHash(hasher.hash("secret-disabled"));
        disabled.setRoleName("ADMIN");
        disabled.setEnabled(false);
        repository.save(disabled);

        ServiceApiKeyEntity revoked = new ServiceApiKeyEntity();
        revoked.setKeyId("svc-revoked");
        revoked.setSecretHash(hasher.hash("secret-revoked"));
        revoked.setRoleName("ADMIN");
        revoked.setEnabled(true);
        revoked.setRevokedAt(Instant.now());
        repository.save(revoked);
    }

    @Test
    void validInvalidAndRoleRestrictedApiKeys() throws Exception {
        mockMvc.perform(get("/api/v1/monitors").header("X-API-Key", "svc-admin.secret-admin"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/monitors").header("X-API-Key", "svc-admin.wrong"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/monitors").header("X-API-Key", "svc-disabled.secret-disabled"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/monitors").header("X-API-Key", "svc-revoked.secret-revoked"))
                .andExpect(status().isUnauthorized());

        String payload = """
                {"name":"A","type":"HTTP","targetUrl":"https://example.com","intervalSec":60,"enabled":true,"timeoutMs":500}
                """;
        mockMvc.perform(post("/api/v1/monitors")
                        .header("X-API-Key", "svc-viewer.secret-viewer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void sensitiveWritesAreRateLimitedWithRetryHeader() throws Exception {
        String payload = """
                {"name":"A","type":"HTTP","targetUrl":"https://example.com","intervalSec":60,"enabled":true,"timeoutMs":500}
                """;
        mockMvc.perform(post("/api/v1/monitors").header("X-API-Key", "svc-admin.secret-admin")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/monitors").header("X-API-Key", "svc-admin.secret-admin")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/monitors").header("X-API-Key", "svc-admin.secret-admin")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        Thread.sleep(1200);
        mockMvc.perform(post("/api/v1/monitors").header("X-API-Key", "svc-admin.secret-admin")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated());
    }

    @Test
    void setupEndpointsAreRateLimitedAsSensitive() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/setup/status")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
