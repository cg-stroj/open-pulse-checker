package io.openpulsechecker.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ProductionProfileConfigTest {

    @Test
    void productionProfileUsesPostgresAndNotH2() throws IOException {
        String prodYaml = new ClassPathResource("application-prod.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(prodYaml.contains("org.postgresql.Driver"));
        assertTrue(prodYaml.contains("jdbc:postgresql://"));
        assertTrue(prodYaml.contains("cors-allowed-origins"));
        assertFalse(prodYaml.contains("cors-allowed-origins: *"));
        assertFalse(prodYaml.toLowerCase().contains("h2"));
    }
}
