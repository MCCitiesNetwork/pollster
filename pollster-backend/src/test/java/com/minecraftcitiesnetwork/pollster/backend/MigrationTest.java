package com.minecraftcitiesnetwork.pollster.backend;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationTest extends AbstractDatabaseTest {

    private static int currentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT MAX(version) FROM pollster_schema_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void migrate_createsAllTables() throws SQLException {
        Set<String> tables = new TreeSet<>();
        try (Connection connection = database.dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()"
                             + " AND table_name LIKE 'pollster\\_%'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        assertEquals(Set.of("pollster_survey_state", "pollster_responses", "pollster_answers",
                "pollster_player_state", "pollster_schema_version"), tables);
    }

    @Test
    void migrate_isIdempotent() throws SQLException {
        try (PollsterDatabase second = PollsterDatabase.open(settings(), Logger.getLogger("test"));
             Connection connection = second.dataSource().getConnection()) {
            assertEquals(1, currentVersion(connection));
        }
    }

    @Test
    void migrate_refusesNewerSchema() throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO pollster_schema_version (version, description) VALUES (99, 'future')");
            try {
                SQLException failure = assertThrows(SQLException.class,
                        () -> PollsterDatabase.open(settings(), Logger.getLogger("test")).close());
                assertTrue(failure.getMessage().contains("newer"), failure.getMessage());
            } finally {
                // The schema is shared by every test class, so leave it as found.
                statement.execute("DELETE FROM pollster_schema_version WHERE version = 99");
            }
        }
    }
}
