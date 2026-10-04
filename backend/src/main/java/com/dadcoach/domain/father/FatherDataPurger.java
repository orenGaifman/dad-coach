package com.dadcoach.domain.father;

import com.dadcoach.common.MaskingUtils;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a father and every row of his own data in Dad Coach, for good (the public data-deletion promise: his
 * phone number, conversation history, preferences and the metadata about him). One transaction, children first.
 *
 * <p>Tables are named here with the column that points at the father - his id (BIGINT) or his external UUID
 * ({@code new UUID(0, id)}). A table or column a database does not have is skipped, so the same purge works on
 * every schema generation (production's base schema predates the migrations in this repository).</p>
 *
 * <p>Kept on purpose: {@code safety_event_records} (7-year legal retention, SPEC-004 Req 24; no link to the
 * father's other data), {@code tool_wishlist} (anonymous once the father row is gone: ON DELETE SET NULL) and
 * {@code api_audit_log} (security audit of API calls). His AI conversations live on the platform and are deleted
 * there ({@code PlatformPersonDeletions}).</p>
 */
@Component
public class FatherDataPurger {

    private static final Logger log = LoggerFactory.getLogger(FatherDataPurger.class);

    enum Key { ID, UUID }

    record Owned(String table, String column, Key key) {
    }

    /** Children first: a table is listed before any table it references. */
    static final List<Owned> OWNED = List.of(
            new Owned("memories", "father_id", Key.UUID),          // memory_versions go with them (ON DELETE CASCADE)
            new Owned("memory_audit_log", "father_id", Key.UUID),
            new Owned("memory", "father_id", Key.ID),
            new Owned("quality_time_commitment", "father_id", Key.ID),
            new Owned("quality_time", "father_id", Key.ID),
            new Owned("weekly_goal", "father_id", Key.ID),
            new Owned("mission", "father_id", Key.ID),
            new Owned("goal", "father_id", Key.ID),
            new Owned("calendar_sync_log", "father_id", Key.ID),
            new Owned("message_log", "father_id", Key.ID),
            new Owned("conversation", "father_id", Key.ID),
            new Owned("workflow_state_transition_log", "father_id", Key.ID),
            new Owned("magic_link", "father_id", Key.ID),
            new Owned("scheduled_response_delivery", "father_id", Key.ID),
            new Owned("child", "father_id", Key.ID),
            new Owned("communication_endpoints", "father_id", Key.UUID),
            new Owned("delivery_records", "father_id", Key.UUID),
            new Owned("media_assets", "father_id", Key.UUID),
            new Owned("ai_telemetry", "father_id", Key.UUID),
            new Owned("ai_profiles", "father_id", Key.UUID),
            new Owned("activation_records", "father_id", Key.UUID),
            new Owned("onboarding_sessions", "father_id", Key.UUID),
            new Owned("language_preferences", "father_id", Key.UUID),
            new Owned("communication_preferences", "father_id", Key.UUID),
            new Owned("families", "father_id", Key.UUID));

    private final JdbcTemplate jdbc;

    public FatherDataPurger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return whether the father existed (false: already purged - nothing to do) */
    @Transactional
    public boolean purge(Long fatherId) {
        List<String> phones = jdbc.queryForList("SELECT phone FROM father WHERE id = ? FOR UPDATE", String.class, fatherId);
        if (phones.isEmpty()) {
            return false;
        }
        UUID fatherUuid = new UUID(0L, fatherId);
        int rows = 0;
        // the embedding retry queue keeps a copy of each memory's text, by memory id
        if (hasColumn("embedding_retry_queue", "memory_id") && hasColumn("memories", "father_id")) {
            rows += jdbc.update("DELETE FROM embedding_retry_queue WHERE memory_id IN (SELECT id FROM memories WHERE father_id = ?)",
                    fatherUuid);
        }
        for (Owned owned : OWNED) {
            if (hasColumn(owned.table(), owned.column())) {
                rows += jdbc.update("DELETE FROM " + owned.table() + " WHERE " + owned.column() + " = ?",
                        owned.key() == Key.ID ? fatherId : fatherUuid);
            }
        }
        jdbc.update("DELETE FROM father WHERE id = ?", fatherId);
        log.info("Purged father and his data: id={}, phone={}, rows={}", fatherId, MaskingUtils.maskPhone(phones.get(0)), rows);
        return true;
    }

    private boolean hasColumn(String table, String column) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = current_schema()"
                + " AND table_name = ? AND column_name = ?", Integer.class, table, column);
        return n != null && n > 0;
    }
}
