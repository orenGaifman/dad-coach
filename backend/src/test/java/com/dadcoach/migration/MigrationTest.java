package com.dadcoach.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.AbstractIntegrationTest;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Both ways a Dad Coach database is built (DECISIONS D-011):
 * <ul>
 *   <li>fresh install - the test database itself, created by Flyway from EMPTY: the V23 baseline, then V24+;</li>
 *   <li>production upgrade - production's real V22 schema and its real Flyway history (checksums as production
 *       recorded them), migrated with validation on: V1-V22 must validate unchanged, then V23+ run.</li>
 * </ul>
 * Both must end in the same schema.
 */
class MigrationTest extends AbstractIntegrationTest {

    /** Production's flyway_schema_history (version, description, checksum) - 2026-10-07. Never change a row. */
    static final List<Object[]> PRODUCTION_HISTORY = List.of(
            new Object[]{1, "consolidated schema", 698178520},
            new Object[]{2, "add pre qt reminder sent column", -1905535489},
            new Object[]{3, "embedding retry queue", -362784144},
            new Object[]{4, "enable pgvector extension", 1110318880},
            new Object[]{5, "memories table", -594959192},
            new Object[]{6, "memories ivfflat index", 434158759},
            new Object[]{7, "memory versions table", -1380721429},
            new Object[]{8, "memory audit log table", -1612662707},
            new Object[]{9, "safety event records table", 144998184},
            new Object[]{10, "embedding retry queue", -209464182},
            new Object[]{11, "enable pgvector extension", -797528587},
            new Object[]{12, "memories table", 148014660},
            new Object[]{13, "memories ivfflat index", -1572551162},
            new Object[]{14, "memory versions table", 414726464},
            new Object[]{15, "memory audit log table", 548551250},
            new Object[]{16, "safety event records table", 204024213},
            new Object[]{17, "fix memories table uuid columns", 731528890},
            new Object[]{18, "add welcome step column", -1289162298},
            new Object[]{19, "add ai decision columns to message log", -2144775574},
            new Object[]{20, "create tool wishlist table", 1051420675},
            new Object[]{21, "create scheduled response delivery table", -2071282410},
            new Object[]{22, "platform person deletion", 1893830936});

    static final List<String> KEPT = List.of("child", "communication_endpoints", "dashboard_session", "father",
            "father_deactivation", "goal", "login_link", "message_log", "platform_person_deletion", "quality_time",
            "scheduled_response_delivery", "site_signup", "staff_user", "template_messages", "tool_idempotency",
            "training_progress", "weekly_goal");

    @Test
    void aFreshDatabaseIsBuiltFromTheBaselineAndHasExactlyTheKeptTables() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE type = 'BASELINE'", String.class))
                .containsExactly("23");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class)).isZero();
        assertThat(tables(jdbc)).containsExactlyElementsOf(KEPT);
    }

    @Test
    void theMigrationFilesAreTheOnesProductionRan() {
        Flyway flyway = Flyway.configure().dataSource(postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword())
                .locations("classpath:db/migration").load();
        Map<String, Integer> checksums = new java.util.HashMap<>();
        for (MigrationInfo info : flyway.info().all()) {
            if (info.getVersion() != null && info.getChecksum() != null && info.getScript().startsWith("V")) {
                checksums.put(info.getVersion().getVersion(), info.getChecksum());
            }
        }
        for (Object[] row : PRODUCTION_HISTORY) {
            assertThat(checksums.get(String.valueOf(row[0]))).as("V%s checksum", row[0]).isEqualTo(row[2]);
        }
    }

    @Test
    void productionUpgradesToTheSameSchemaAFreshInstallHas() throws Exception {
        jdbc.execute("DROP DATABASE IF EXISTS prodlike");
        jdbc.execute("CREATE DATABASE prodlike");
        String url = postgres().getJdbcUrl().replace("/dadcoach", "/prodlike");
        DriverManagerDataSource prod = new DriverManagerDataSource(url, postgres().getUsername(), postgres().getPassword());
        try (Connection c = prod.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/production-schema-v22.sql"));
        }
        JdbcTemplate prodJdbc = new JdbcTemplate(prod);
        int rank = 1;
        for (Object[] row : PRODUCTION_HISTORY) {
            prodJdbc.update("INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, "
                            + "installed_by, execution_time, success) VALUES (?, ?, ?, 'SQL', ?, ?, 'postgres', 1, true)",
                    rank++, String.valueOf(row[0]), row[1], "V" + row[0] + "__" + ((String) row[1]).replace(' ', '_') + ".sql", row[2]);
        }
        // a father mid-onboarding with a child and an endpoint (production's one real father has this shape)
        prodJdbc.update("INSERT INTO father (phone, display_name, status, created_at) VALUES ('+19995550001', 'אבא', 'ONBOARDING', now())");
        prodJdbc.update("INSERT INTO child (father_id, name, birth_date, created_at, updated_at, status) "
                + "VALUES (1, 'נועה', '2020-01-01', now(), now(), 'ACTIVE')");

        Flyway flyway = Flyway.configure().dataSource(prod).locations("classpath:db/migration")
                .outOfOrder(true).validateOnMigrate(true).load();
        flyway.migrate();

        assertThat(tables(prodJdbc)).containsExactlyElementsOf(KEPT);
        assertThat(columns(prodJdbc)).containsExactlyElementsOf(columns(jdbc));
        assertThat(indexes(prodJdbc)).containsExactlyElementsOf(indexes(jdbc));
        assertThat(constraints(prodJdbc)).containsExactlyElementsOf(constraints(jdbc));
        assertThat(prodJdbc.queryForObject("SELECT count(*) FROM father", Integer.class)).isEqualTo(1);
        assertThat(prodJdbc.queryForObject("SELECT count(*) FROM child", Integer.class)).isEqualTo(1);
    }

    private static List<String> tables(JdbcTemplate j) {
        return j.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' "
                + "AND table_name <> 'flyway_schema_history' ORDER BY 1", String.class);
    }

    private static List<String> columns(JdbcTemplate j) {
        return j.queryForList("SELECT table_name || '.' || column_name || ' ' || data_type || ' ' || is_nullable || ' ' "
                + "|| coalesce(column_default, '') FROM information_schema.columns WHERE table_schema = 'public' "
                + "AND table_name <> 'flyway_schema_history' ORDER BY 1", String.class);
    }

    private static List<String> indexes(JdbcTemplate j) {
        return j.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' "
                + "AND tablename <> 'flyway_schema_history' ORDER BY 1", String.class);
    }

    private static List<String> constraints(JdbcTemplate j) {
        return j.queryForList("SELECT conrelid::regclass || ' ' || conname || ' ' || pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE connamespace = 'public'::regnamespace AND conrelid::regclass::text <> 'flyway_schema_history' ORDER BY 1",
                String.class);
    }
}
