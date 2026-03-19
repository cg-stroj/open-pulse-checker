package io.openpulsechecker.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openpulsechecker.support.H2TestDatabaseSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "openpulse.security.cors-allowed-origins=http://localhost:5173",
        "openpulse.security.cors-allow-credentials=true"
})
@AutoConfigureMockMvc
class CorsConfigurationIntegrationTest extends H2TestDatabaseSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightRequestFromAllowedOriginReturnsCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void requestFromBlockedOriginIsRejectedWithoutCorsAllowOriginHeader() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void wildcardOriginWithCredentialsIsRejected() {
        SecurityConfig securityConfig = new SecurityConfig(new SecurityProperties("db", List.of("*"), true));

        assertThrows(IllegalStateException.class, securityConfig::corsConfigurationSource);
    }
}
