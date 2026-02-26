package io.openpulsechecker.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openpulsechecker.service.HttpCheckClient;
import io.openpulsechecker.service.HttpCheckOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@AutoConfigureMockMvc
class MonitorApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HttpCheckClient httpCheckClient;

    @Test
    void createAndRunCheckFlow() throws Exception {
        given(httpCheckClient.execute(anyString(), anyInt()))
                .willReturn(new HttpCheckOutcome(true, 200, 50L, null));

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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("HTTP"))
                .andReturn();

        String response = created.getResponse().getContentAsString();
        String id = response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/monitors/" + id + "/run-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitorId").value(id))
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
