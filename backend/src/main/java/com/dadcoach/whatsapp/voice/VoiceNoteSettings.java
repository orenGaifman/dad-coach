package com.dadcoach.whatsapp.voice;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** D-027: the admin's on/off for voice notes, for the whole product. On until the admin turns it off (no row = on). */
@Repository
public class VoiceNoteSettings {

    static final String KEY = "voice_notes.enabled";

    private final JdbcTemplate jdbc;

    public VoiceNoteSettings(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean enabled() {
        List<String> values = jdbc.queryForList("SELECT value FROM system_setting WHERE key = ?", String.class, KEY);
        return values.isEmpty() || Boolean.parseBoolean(values.get(0));
    }

    public void setEnabled(boolean enabled) {
        jdbc.update("""
                INSERT INTO system_setting (key, value, updated_at) VALUES (?, ?, now())
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at
                """, KEY, String.valueOf(enabled));
    }
}
