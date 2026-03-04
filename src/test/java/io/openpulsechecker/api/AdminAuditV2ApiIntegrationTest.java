package io.openpulsechecker.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openpulsechecker.audit.AuditService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class AdminAuditV2ApiIntegrationTest extends H2TestDatabaseSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuditService auditService;

    @Test
    void filtersByCombinationIncludingDateRangeAndSearch() throws Exception {
        auditService.log("ops-bot", "INCIDENT_RESOLVE", "incident/987", "SUCCESS", "resolved cleanly");
        auditService.log("ops-bot", "INCIDENT_RESOLVE", "incident/654", "FAILURE", "timeout while resolving");

        mockMvc.perform(get("/api/v2/admin/audit-events")
                        .with(httpBasic("admin", "admin-change-me"))
                        .queryParam("actor", "ops")
                        .queryParam("action", "INCIDENT_RESOLVE")
                        .queryParam("resource", "incident/987")
                        .queryParam("outcome", "SUCCESS")
                        .queryParam("q", "cleanly")
                        .queryParam("fromAt", Instant.now().minusSeconds(600).toString())
                        .queryParam("toAt", Instant.now().plusSeconds(60).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actor").value("ops-bot"))
                .andExpect(jsonPath("$.items[0].resource").value("incident/987"))
                .andExpect(jsonPath("$.items[0].outcome").value("SUCCESS"));
    }

    @Test
    void supportsCursorPagination() throws Exception {
        for (int i = 0; i < 4; i++) {
            auditService.log("pager", "ALERT_ACK", "incident/" + i, "SUCCESS", "ack" + i);
            Thread.sleep(5);
        }

        MvcResult firstPage = mockMvc.perform(get("/api/v2/admin/audit-events")
                        .with(httpBasic("admin", "admin-change-me"))
                        .queryParam("actor", "pager")
                        .queryParam("size", "2")
                        .queryParam("cursorMode", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0]", notNullValue()))
                .andReturn();

        String cursor = com.jayway.jsonpath.JsonPath.read(firstPage.getResponse().getContentAsString(), "$.nextCursor");

        mockMvc.perform(get("/api/v2/admin/audit-events")
                        .with(httpBasic("admin", "admin-change-me"))
                        .queryParam("actor", "pager")
                        .queryParam("size", "2")
                        .queryParam("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0]", notNullValue()));
    }

    @Test
    void exportAppliesFiltersAndGuardrails() throws Exception {
        auditService.log("exporter", "INCIDENT_EXPORT", "incident/222", "SUCCESS", "csv line");

        mockMvc.perform(get("/api/v2/admin/audit-events/export")
                        .with(httpBasic("admin", "admin-change-me"))
                        .queryParam("format", "csv")
                        .queryParam("actor", "exporter")
                        .queryParam("limit", "999999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INCIDENT_EXPORT")));

        mockMvc.perform(get("/api/v2/admin/audit-events/export")
                        .with(httpBasic("admin", "admin-change-me"))
                        .queryParam("format", "json")
                        .queryParam("actor", "exporter"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"action\":\"INCIDENT_EXPORT\"")));

        mockMvc.perform(get("/api/v2/admin/audit-events/export")
                        .with(httpBasic("admin", "admin-change-me"))
                        .queryParam("format", "xml"))
                .andExpect(status().isBadRequest());
    }
}
