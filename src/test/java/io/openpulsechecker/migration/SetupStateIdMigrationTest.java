package io.openpulsechecker.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SetupStateIdMigrationTest {

    @Test
    void migrationSqlUpgradesLegacySetupStateIdType() throws SQLException, IOException {
        String dbName = "setup_state_id_" + UUID.randomUUID();
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE setup_state (
                        id SMALLINT PRIMARY KEY,
                        setup_locked BOOLEAN NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        CONSTRAINT chk_setup_state_singleton CHECK (id = 1)
                    )
                    """);
            statement.execute("INSERT INTO setup_state (id, setup_locked, updated_at) VALUES (1, FALSE, CURRENT_TIMESTAMP)");
        }

        assertThat(columnType(jdbcUrl, "SETUP_STATE", "ID")).isEqualTo("SMALLINT");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(loadMigrationSql("V10__setup_state_id_integer.sql"));
        }

        assertThat(columnType(jdbcUrl, "SETUP_STATE", "ID")).isEqualTo("INTEGER");
    }

    private String loadMigrationSql(String migrationFile) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream("db/migration/" + migrationFile)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String columnType(String jdbcUrl, String tableName, String columnName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             PreparedStatement statement = connection.prepareStatement(
                     """
                     SELECT DATA_TYPE
                     FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_NAME = ?
                       AND COLUMN_NAME = ?
                     """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);

            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1).toUpperCase(Locale.ROOT);
            }
        }
    }
}
