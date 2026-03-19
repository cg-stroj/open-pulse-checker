package io.openpulsechecker.support;

import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class H2TestDatabaseSupport {

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        String dbName = "openpulse_" + UUID.randomUUID().toString().replace("-", "");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
    }
}
