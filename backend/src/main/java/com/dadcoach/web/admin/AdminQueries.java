package com.dadcoach.web.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The admin's read model: plain SQL over the product tables (father, child, quality_time, weekly_goal, message_log,
 * scheduled_response_delivery, login_link, platform_person_deletion). Read-only; every write goes through a service.
 */
@Component
public class AdminQueries {

    private final JdbcTemplate jdbc;

    public AdminQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record FatherRow(long id, String name, String phone, String status, String belt, int completed,
                            boolean calendarConnected, Instant createdAt, Instant lastActivity, int children,
                            boolean deletionPending) {
    }

    public record DeliveryRow(String kind, Long fatherId, String fatherName, String what, String status, String reason,
                              Instant at) {
    }

    private static final String FATHER_COLUMNS = """
            SELECT f.id, f.display_name, f.phone, f.status, f.current_belt, f.total_quality_times_completed,
                   (COALESCE(f.google_calendar_enabled, false) AND f.google_refresh_token IS NOT NULL
                        AND f.google_refresh_token <> '') AS calendar_connected,
                   f.created_at,
                   GREATEST(f.last_interaction_at, (SELECT max(m.created_at) FROM message_log m WHERE m.father_id = f.id)) AS last_activity,
                   (SELECT count(*) FROM child c WHERE c.father_id = f.id AND c.status = 'ACTIVE') AS children,
                   EXISTS (SELECT 1 FROM platform_person_deletion d WHERE d.father_id = f.id AND d.completed_at IS NULL) AS deletion_pending
            FROM father f
            """;

    public List<FatherRow> fathers(String query, String status, int limit) {
        StringBuilder sql = new StringBuilder(FATHER_COLUMNS).append(" WHERE 1 = 1");
        List<Object> args = new java.util.ArrayList<>();
        if (query != null && !query.isBlank()) {
            String q = query.strip();
            String digits = q.replaceAll("[^0-9]", "");
            sql.append(" AND (f.display_name ILIKE ?");
            args.add("%" + q.replace("%", "").replace("_", "") + "%");
            if (digits.length() >= 3) {
                sql.append(" OR regexp_replace(f.phone, '[^0-9]', '', 'g') LIKE ?");
                args.add("%" + (digits.startsWith("0") ? digits.substring(1) : digits) + "%");
            }
            sql.append(')');
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND f.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY last_activity DESC NULLS LAST, f.id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, i) -> new FatherRow(rs.getLong("id"), rs.getString("display_name"),
                rs.getString("phone"), rs.getString("status"), rs.getString("current_belt"),
                rs.getInt("total_quality_times_completed"), rs.getBoolean("calendar_connected"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("last_activity")), rs.getInt("children"),
                rs.getBoolean("deletion_pending")), args.toArray());
    }

    public Map<String, Long> fathersByStatus() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        jdbc.query("SELECT status, count(*) AS n FROM father GROUP BY status ORDER BY status",
                rs -> {
                    counts.put(rs.getString("status"), rs.getLong("n"));
                });
        return counts;
    }

    public long activeSince(Instant since) {
        return count("SELECT count(DISTINCT father_id) FROM message_log WHERE direction = 'INBOUND' AND created_at >= ?",
                Timestamp.from(since));
    }

    public long goalsSet(java.time.LocalDate weekStart) {
        return count("SELECT count(*) FROM weekly_goal WHERE week_start_date = ? AND status <> 'CANCELLED'", weekStart);
    }

    public long goalsMet(java.time.LocalDate weekStart) {
        return count("SELECT count(*) FROM weekly_goal WHERE week_start_date = ? AND status <> 'CANCELLED'"
                + " AND (status = 'COMPLETED' OR actual_minutes >= target_hours * 60)", weekStart);
    }

    public long sessionsBetween(Instant from, Instant to, String status) {
        return status == null
                ? count("SELECT count(*) FROM quality_time WHERE scheduled_start >= ? AND scheduled_start < ? AND status <> 'CANCELLED'",
                        Timestamp.from(from), Timestamp.from(to))
                : count("SELECT count(*) FROM quality_time WHERE scheduled_start >= ? AND scheduled_start < ? AND status = ?",
                        Timestamp.from(from), Timestamp.from(to), status);
    }

    public long failedDeliveriesSince(Instant since) {
        return count("SELECT count(*) FROM scheduled_response_delivery WHERE status = 'FAILED' AND created_at >= ?",
                Timestamp.from(since))
                + count("SELECT count(*) FROM login_link WHERE delivery_status = 'FAILED' AND created_at >= ?", Timestamp.from(since));
    }

    public long pendingDeletions() {
        return count("SELECT count(*) FROM platform_person_deletion WHERE completed_at IS NULL");
    }

    /** Messages that did not reach a father (scheduled coach messages, login links), newest first. */
    public List<DeliveryRow> undelivered(Instant since, Long fatherId, int limit) {
        String fatherFilter = fatherId == null ? "" : " AND x.father_id = " + fatherId.longValue();
        return jdbc.query("""
                SELECT x.* , f.display_name FROM (
                  SELECT 'SCHEDULED' AS kind, d.father_id, d.target_state_key AS what, d.status, d.failure_reason AS reason, d.created_at AS at
                  FROM scheduled_response_delivery d WHERE d.status = 'FAILED' AND d.created_at >= ?
                  UNION ALL
                  SELECT 'LOGIN_LINK', l.father_id, NULL, l.delivery_status, l.delivery_error, l.created_at
                  FROM login_link l WHERE l.delivery_status = 'FAILED' AND l.created_at >= ?
                ) x LEFT JOIN father f ON f.id = x.father_id WHERE 1 = 1""" + fatherFilter + " ORDER BY x.at DESC LIMIT ?",
                (rs, i) -> new DeliveryRow(rs.getString("kind"), (Long) rs.getObject("father_id"), rs.getString("display_name"),
                        rs.getString("what"), rs.getString("status"), rs.getString("reason"), instant(rs.getTimestamp("at"))),
                Timestamp.from(since), Timestamp.from(since), limit);
    }

    /** Every scheduled coach message to one father (delivered or not), newest first. */
    public List<DeliveryRow> deliveriesOf(long fatherId, int limit) {
        return jdbc.query("""
                SELECT 'SCHEDULED' AS kind, d.father_id, d.target_state_key AS what, d.status, d.failure_reason AS reason,
                       COALESCE(d.completed_at, d.created_at) AS at
                FROM scheduled_response_delivery d WHERE d.father_id = ? ORDER BY d.created_at DESC LIMIT ?""",
                (rs, i) -> new DeliveryRow(rs.getString("kind"), rs.getLong("father_id"), null, rs.getString("what"),
                        rs.getString("status"), rs.getString("reason"), instant(rs.getTimestamp("at"))), fatherId, limit);
    }

    public List<Map<String, Object>> loginLinksOf(long fatherId, int limit) {
        return jdbc.queryForList("""
                SELECT created_at AS "createdAt", expires_at AS "expiresAt", used_at AS "usedAt",
                       last_used_at AS "lastUsedAt", use_count AS "useCount", revoked_at AS "revokedAt",
                       delivery_status AS "deliveryStatus", delivery_error AS "deliveryError",
                       receipt_status AS "receiptStatus"
                FROM login_link WHERE father_id = ? ORDER BY created_at DESC LIMIT ?""", fatherId, limit);
    }

    public long liveSessionsOf(long fatherId, Instant now) {
        return count("SELECT count(*) FROM dashboard_session WHERE father_id = ? AND revoked_at IS NULL AND idle_expires_at > ?",
                fatherId, Timestamp.from(now));
    }

    public Map<String, Object> deletionOf(long fatherId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT requested_at AS "requestedAt", attempts, next_attempt_at AS "nextAttemptAt", last_error AS "lastError",
                       completed_at AS "completedAt", outcome, purge_local AS "purgeLocal"
                FROM platform_person_deletion WHERE father_id = ?""", fatherId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> pendingDeletionRows(int limit) {
        return jdbc.queryForList("""
                SELECT father_id AS "fatherId", requested_at AS "requestedAt", attempts, next_attempt_at AS "nextAttemptAt",
                       last_error AS "lastError", purge_local AS "purgeLocal"
                FROM platform_person_deletion WHERE completed_at IS NULL ORDER BY requested_at LIMIT ?""", limit);
    }

    public Instant lastInbound() {
        return instant(jdbc.queryForObject("SELECT max(created_at) FROM message_log WHERE direction = 'INBOUND'", Timestamp.class));
    }

    public Instant lastOutbound() {
        return instant(jdbc.queryForObject("SELECT max(created_at) FROM message_log WHERE direction = 'OUTBOUND'", Timestamp.class));
    }

    public Map<String, Object> lastFailure() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT failure_reason AS "reason", created_at AS "at" FROM scheduled_response_delivery
                WHERE status = 'FAILED' ORDER BY created_at DESC LIMIT 1""");
        return rows.isEmpty() ? null : rows.get(0);
    }

    // deactivation (V32)

    public String previousStatus(long fatherId) {
        List<String> rows = jdbc.queryForList("SELECT previous_status FROM father_deactivation WHERE father_id = ?", String.class, fatherId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Instant deactivatedAt(long fatherId) {
        List<Timestamp> rows = jdbc.queryForList("SELECT deactivated_at FROM father_deactivation WHERE father_id = ?", Timestamp.class, fatherId);
        return rows.isEmpty() ? null : instant(rows.get(0));
    }

    public void recordDeactivation(long fatherId, String previousStatus, Instant at, java.util.UUID by) {
        jdbc.update("""
                INSERT INTO father_deactivation (father_id, previous_status, deactivated_at, deactivated_by) VALUES (?, ?, ?, ?)
                ON CONFLICT (father_id) DO NOTHING""", fatherId, previousStatus, Timestamp.from(at), by);
    }

    public void clearDeactivation(long fatherId) {
        jdbc.update("DELETE FROM father_deactivation WHERE father_id = ?", fatherId);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
