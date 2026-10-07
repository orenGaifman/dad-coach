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
 * every schema generation.</p>
 *
 * <p>His tool idempotency rows (keyed by his WhatsApp number) go too. His AI conversations live on the platform
 * and are deleted there ({@code PlatformPersonDeletions}).</p>
 */
@Component
public class FatherDataPurger {

    private static final Logger log = LoggerFactory.getLogger(FatherDataPurger.class);

    enum Key { ID, UUID }

    record Owned(String table, String column, Key key) {
    }

    /** Children first: a table is listed before any table it references. */
    static final List<Owned> OWNED = List.of(
            new Owned("quality_time", "father_id", Key.ID),
            new Owned("weekly_goal", "father_id", Key.ID),
            new Owned("goal", "father_id", Key.ID),
            new Owned("message_log", "father_id", Key.ID),
            new Owned("scheduled_response_delivery", "father_id", Key.ID),
            new Owned("child", "father_id", Key.ID),
            new Owned("communication_endpoints", "father_id", Key.UUID));

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
        if (hasColumn("tool_idempotency", "actor_ref")) {
            rows += jdbc.update("DELETE FROM tool_idempotency WHERE actor_ref = ?", "whatsapp:" + phones.get(0));
        }
        if (hasColumn("site_signup", "phone")) { // the site's signup is keyed by phone, not by father
            rows += jdbc.update("DELETE FROM site_signup WHERE phone = ?", phones.get(0));
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
