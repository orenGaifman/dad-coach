package com.dadcoach.domain.father;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The father purge on a real Postgres with the production schema's shape: every owned table (by id or by external
 * UUID, with the base schema's FKs and cascades), the memory text copies in the embedding retry queue, tables a
 * database lacks skipped, and the intentional exceptions kept.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FatherDataPurgerTest {

    private PostgreSQLContainer<?> postgres;
    private JdbcTemplate jdbc;
    private FatherDataPurger purger;

    @BeforeAll
    void start() {
        postgres = new PostgreSQLContainer<>("postgres:16");
        postgres.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.execute("CREATE TABLE father (id BIGSERIAL PRIMARY KEY, phone VARCHAR(32) NOT NULL UNIQUE, status VARCHAR(20))");
        jdbc.execute("CREATE TABLE child (id BIGSERIAL PRIMARY KEY, father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE)");
        jdbc.execute("CREATE TABLE quality_time_commitment (id BIGSERIAL PRIMARY KEY, father_id BIGINT NOT NULL REFERENCES father(id),"
                + " child_id BIGINT REFERENCES child(id))");
        jdbc.execute("CREATE TABLE goal (id BIGSERIAL PRIMARY KEY, father_id BIGINT NOT NULL REFERENCES father(id))");
        jdbc.execute("CREATE TABLE mission (id BIGSERIAL PRIMARY KEY, father_id BIGINT NOT NULL REFERENCES father(id), goal_id BIGINT REFERENCES goal(id))");
        jdbc.execute("CREATE TABLE message_log (id BIGSERIAL PRIMARY KEY, father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE, body TEXT)");
        jdbc.execute("CREATE TABLE magic_link (id BIGSERIAL PRIMARY KEY, father_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE memories (id UUID PRIMARY KEY, father_id UUID NOT NULL, content TEXT)");
        jdbc.execute("CREATE TABLE memory_versions (id BIGSERIAL PRIMARY KEY, memory_id UUID NOT NULL REFERENCES memories(id) ON DELETE CASCADE)");
        jdbc.execute("CREATE TABLE embedding_retry_queue (id BIGSERIAL PRIMARY KEY, memory_id UUID NOT NULL, content TEXT)");
        jdbc.execute("CREATE TABLE memory_audit_log (id BIGSERIAL PRIMARY KEY, father_id UUID NOT NULL)");
        jdbc.execute("CREATE TABLE families (id BIGSERIAL PRIMARY KEY, father_id UUID NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE onboarding_sessions (id BIGSERIAL PRIMARY KEY, father_id UUID)");
        jdbc.execute("CREATE TABLE delivery_records (id BIGSERIAL PRIMARY KEY, father_id UUID NOT NULL)");
        jdbc.execute("CREATE TABLE safety_event_records (id BIGSERIAL PRIMARY KEY, father_id UUID NOT NULL)");
        jdbc.execute("CREATE TABLE tool_wishlist (id BIGSERIAL PRIMARY KEY, father_id BIGINT REFERENCES father(id) ON DELETE SET NULL)");
        jdbc.execute("CREATE TABLE scheduled_response_delivery (id BIGSERIAL PRIMARY KEY, father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE)");
        purger = new FatherDataPurger(jdbc);
    }

    @AfterAll
    void stop() {
        postgres.stop();
    }

    @Test
    @DisplayName("every row of the father's own data goes; another father's stays; legal and anonymous records stay")
    void purgesEverythingOfTheFatherOnly() {
        long dana = seed("+972502220001");
        long other = seed("+972502220002");

        assertThat(purger.purge(dana)).isTrue();

        for (String table : new String[] {"child", "quality_time_commitment", "goal", "mission", "message_log", "magic_link",
                "scheduled_response_delivery"}) {
            assertThat(n("SELECT count(*) FROM " + table + " WHERE father_id = ?", dana)).as(table).isZero();
            assertThat(n("SELECT count(*) FROM " + table + " WHERE father_id = ?", other)).as(table + " of another father").isEqualTo(1);
        }
        for (String table : new String[] {"memories", "memory_audit_log", "families", "onboarding_sessions", "delivery_records"}) {
            assertThat(n("SELECT count(*) FROM " + table + " WHERE father_id = ?", new UUID(0, dana))).as(table).isZero();
            assertThat(n("SELECT count(*) FROM " + table + " WHERE father_id = ?", new UUID(0, other))).as(table + " of another father").isEqualTo(1);
        }
        assertThat(n("SELECT count(*) FROM embedding_retry_queue WHERE content = ?", "dana memory +972502220001")).isZero();
        assertThat(n("SELECT count(*) FROM embedding_retry_queue WHERE content = ?", "dana memory +972502220002")).isEqualTo(1);
        assertThat(n("SELECT count(*) FROM memory_versions v JOIN memories m ON m.id = v.memory_id WHERE m.father_id = ?",
                new UUID(0, other))).as("another father's memory versions stay").isEqualTo(1);
        assertThat(n("SELECT count(*) FROM memory_versions v WHERE NOT EXISTS (SELECT 1 FROM memories m WHERE m.id = v.memory_id)"))
                .as("the father's memory versions went with his memories").isZero();
        assertThat(n("SELECT count(*) FROM father WHERE id = ?", dana)).isZero();
        assertThat(n("SELECT count(*) FROM safety_event_records WHERE father_id = ?", new UUID(0, dana)))
                .as("7-year legal retention (SPEC-004 Req 24)").isEqualTo(1);
        assertThat(n("SELECT count(*) FROM tool_wishlist WHERE father_id IS NULL")).as("anonymous once the father is gone").isEqualTo(1);
    }

    @Test
    @DisplayName("tables this database does not have are skipped; purging twice is a no-op")
    void missingTablesAndRepeats() {
        long dana = seed("+972502220003");

        assertThat(purger.purge(dana)).isTrue();
        assertThat(purger.purge(dana)).isFalse();
        assertThat(FatherDataPurger.OWNED).extracting(o -> o.table()).contains("weekly_goal", "conversation", "ai_telemetry");
    }

    private long seed(String phone) {
        Long id = jdbc.queryForObject("INSERT INTO father (phone) VALUES (?) RETURNING id", Long.class, phone);
        UUID uuid = new UUID(0, id);
        Long child = jdbc.queryForObject("INSERT INTO child (father_id) VALUES (?) RETURNING id", Long.class, id);
        jdbc.update("INSERT INTO quality_time_commitment (father_id, child_id) VALUES (?, ?)", id, child);
        Long goal = jdbc.queryForObject("INSERT INTO goal (father_id) VALUES (?) RETURNING id", Long.class, id);
        jdbc.update("INSERT INTO mission (father_id, goal_id) VALUES (?, ?)", id, goal);
        jdbc.update("INSERT INTO message_log (father_id, body) VALUES (?, 'hi')", id);
        jdbc.update("INSERT INTO magic_link (father_id) VALUES (?)", id);
        jdbc.update("INSERT INTO scheduled_response_delivery (father_id) VALUES (?)", id);
        UUID memory = UUID.randomUUID();
        jdbc.update("INSERT INTO memories (id, father_id, content) VALUES (?, ?, 'x')", memory, uuid);
        jdbc.update("INSERT INTO memory_versions (memory_id) VALUES (?)", memory);
        jdbc.update("INSERT INTO embedding_retry_queue (memory_id, content) VALUES (?, ?)", memory, "dana memory " + phone);
        jdbc.update("INSERT INTO memory_audit_log (father_id) VALUES (?)", uuid);
        jdbc.update("INSERT INTO families (father_id) VALUES (?)", uuid);
        jdbc.update("INSERT INTO onboarding_sessions (father_id) VALUES (?)", uuid);
        jdbc.update("INSERT INTO delivery_records (father_id) VALUES (?)", uuid);
        jdbc.update("INSERT INTO safety_event_records (father_id) VALUES (?)", uuid);
        jdbc.update("INSERT INTO tool_wishlist (father_id) VALUES (?)", id);
        return id;
    }

    private int n(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
