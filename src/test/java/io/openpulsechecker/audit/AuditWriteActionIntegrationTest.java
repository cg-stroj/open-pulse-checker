package io.openpulsechecker.audit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class AuditWriteActionIntegrationTest extends H2TestDatabaseSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    void monitorCreateWritesAuditEntry() throws Exception {
        String payload = """
                {
                  "name":"Docs",
                  "type":"HTTP",
                  "targetUrl":"https://example.com",
                  "intervalSec":60,
                  "enabled":true,
                  "timeoutMs":1200
                }
                """;

        mockMvc.perform(post("/api/v1/monitors")
                        .with(httpBasic("admin", "admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        AuditEventEntity latest = auditEventRepository.findTopByOrderByOccurredAtDesc();
        org.junit.jupiter.api.Assertions.assertEquals("MONITOR_CREATE", latest.getAction());
        org.junit.jupiter.api.Assertions.assertEquals("admin", latest.getUsername());
    }
}
