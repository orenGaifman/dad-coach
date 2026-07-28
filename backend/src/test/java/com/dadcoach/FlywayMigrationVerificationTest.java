package com.dadcoach;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies all Flyway migrations (V1 through V8.011) apply successfully
 * against a real PostgreSQL instance. Validates:
 * - All V8.xxx migrations execute without errors
 * - V8 tables exist with correct structure
 * - FK ordering is correct (V8.003 father_streaks does not have invalid FK)
 * - No conflicts with existing SPEC-002/SPEC-006/SPEC-007 tables
 *
 * Note: V13 has a pre-existing issue with now() in partial index predicates.
 * This test targets V8.xxx migration correctness specifically.
 *
 * Requirement: 24.3
 */
class FlywayMigrationVerificationTest {

    static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startPostgres() {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine");
        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private Flyway createFlyway() {
        FluentConfiguration config = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        return config.load();
    }

    /**
     * Runs migrations up to the target version, stopping before V13 (which has a
     * pre-existing now() IMMUTABLE issue unrelated to our V8 work).
     */
    private void migrateUpToV12() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("12")
                .load();
        flyway.migrate();
    }

    @Test
    void allMigrationsUpToV12ApplySuccessfully() {
        migrateUpToV12();

        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("12")
                .load();

        MigrationInfoService info = flyway.info();
        MigrationInfo[] applied = info.applied();

        assertThat(applied).isNotEmpty();
        assertThat(Arrays.stream(applied).noneMatch(m -> m.getState().isFailed()))
                .as("No migrations should be in failed state")
                .isTrue();
    }

    @Test
    void allV8MigrationsAreApplied() {
        migrateUpToV12();

        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("12")
                .load();

        MigrationInfoService info = flyway.info();
        List<String> v8Versions = Arrays.stream(info.applied())
                .map(m -> m.getVersion().toString())
                .filter(v -> v.startsWith("8."))
                .collect(Collectors.toList());

        assertThat(v8Versions).containsExactlyInAnyOrder(
                "8.001", "8.002", "8.003", "8.004", "8.005",
                "8.006", "8.007", "8.008", "8.009", "8.010", "8.011"
        );
    }

    @Test
    void v8TablesExistWithCorrectStructure() throws Exception {
        migrateUpToV12();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

            // Verify all V8 tables exist
            String[] expectedTables = {
                    "growth_signals", "father_belts", "father_streaks",
                    "achievements", "father_achievements",
                    "milestones", "father_milestones",
                    "celebration_events", "activity_reports",
                    "activity_feed_items", "statistics_aggregates"
            };

            for (String table : expectedTables) {
                try (ResultSet rs = conn.getMetaData().getTables(null, "public", table, null)) {
                    assertThat(rs.next())
                            .as("Table '%s' should exist", table)
                            .isTrue();
                }
            }
        }
    }

    @Test
    void fatherTableExistsFromEarlierSpecs() throws Exception {
        migrateUpToV12();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

            // Verify the father table (from SPEC-002) exists with 'id' column
            try (ResultSet rs = conn.getMetaData().getTables(null, "public", "father", null)) {
                assertThat(rs.next())
                        .as("Table 'father' should exist from earlier specs")
                        .isTrue();
            }

            // Verify the father table has an 'id' column (BIGSERIAL PK)
            try (ResultSet rs = conn.getMetaData().getColumns(null, "public", "father", "id")) {
                assertThat(rs.next())
                        .as("Column 'father.id' should exist")
                        .isTrue();
            }
        }
    }

    @Test
    void v8003FatherStreaksHasNoFkToFatherTable() throws Exception {
        // V8.003 father_streaks uses UUID father_id without FK to father(id BIGSERIAL)
        // This is intentional — FK will be added when father table migrates to UUID PKs
        migrateUpToV12();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // Verify father_streaks.father_id has UUID type and no FK constraint
            ResultSet rs = stmt.executeQuery(
                    "SELECT data_type FROM information_schema.columns " +
                    "WHERE table_name = 'father_streaks' AND column_name = 'father_id'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("data_type")).isEqualTo("uuid");

            // Verify there are no foreign key constraints on father_streaks
            ResultSet fkRs = stmt.executeQuery(
                    "SELECT constraint_name FROM information_schema.table_constraints " +
                    "WHERE table_name = 'father_streaks' AND constraint_type = 'FOREIGN KEY'");
            assertThat(fkRs.next())
                    .as("father_streaks should have no FK constraints (UUID/BIGSERIAL type mismatch)")
                    .isFalse();
        }
    }

    @Test
    void seedDataIsInsertedCorrectly() throws Exception {
        migrateUpToV12();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // Verify achievements were seeded (V8.010)
            ResultSet achRs = stmt.executeQuery("SELECT COUNT(*) FROM achievements");
            achRs.next();
            assertThat(achRs.getInt(1))
                    .as("Should have 15 seeded achievements")
                    .isEqualTo(15);

            // Verify milestones were seeded (V8.011)
            ResultSet msRs = stmt.executeQuery("SELECT COUNT(*) FROM milestones");
            msRs.next();
            assertThat(msRs.getInt(1))
                    .as("Should have seeded milestones (mission count + account age)")
                    .isGreaterThanOrEqualTo(8);
        }
    }
}
