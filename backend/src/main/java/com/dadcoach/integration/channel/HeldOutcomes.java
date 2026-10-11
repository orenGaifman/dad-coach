package com.dadcoach.integration.channel;

import com.dadcoach.integration.platform.SharedNumberGate;
import com.dadcoach.whatsapp.DeliveryReceipts;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-042 (Unified Workflow Phase 6.1): what became of a message the shared-number gateway held, as the platform reports
 * it (spec v2 §v2.7). The two rows that keep a gateway id - a scheduled coach message
 * ({@code scheduled_response_delivery.gateway_hold_id}) and a dashboard link ({@code login_link.gateway_hold_id}) -
 * leave HELD exactly once, by one conditional UPDATE each:
 * <ul>
 *   <li>SENT: ACCEPTED (a link: receipt_status NULL = accepted) with Meta's wamid, so Meta's receipts match it from now
 *       on; the latest status the gateway saw and any receipt kept for the wamid are applied, forward only.</li>
 *   <li>FAILED: FAILED, reason {@code GATEWAY_<errorCode>} - the gateway's send was refused or never written.</li>
 *   <li>EXPIRED: FAILED, reason {@code GATEWAY_HELD_EXPIRED[_<closedReason>]} - the gateway closed it without ever
 *       sending it (not among the latest 10 when he came back, or held longer than 24 h). He never got it.</li>
 *   <li>UNKNOWN: a scheduled message turns UNKNOWN, a link keeps delivery_status SENT with receipt_status UNKNOWN -
 *       the send may have reached Meta (5xx, timeout after write). Never counted as failed.</li>
 * </ul>
 * Nothing here sends anything, and no reader of these statuses sends again (D-042 reader analysis): a later outcome
 * changes what the admin sees, the platform's answer to a replayed callback, and - for a FAILED/EXPIRED link - that the
 * next time he asks for his page a new button goes out instead of "it is on your screen".
 */
@Service
public class HeldOutcomes {

    private static final Logger log = LoggerFactory.getLogger(HeldOutcomes.class);

    public enum Outcome { SENT, FAILED, UNKNOWN, EXPIRED }

    /** What the report changed: whether a row holds the id, the rows that left HELD, the moves of the latest status. */
    public record Applied(boolean known, int scheduled, int links, int statusMoves) {
        public boolean applied() {
            return scheduled + links + statusMoves > 0;
        }
    }

    private final JdbcTemplate jdbc;
    private final DeliveryReceipts receipts;
    private final Clock clock;
    private final boolean receiptsEnabled;
    private volatile boolean enabled;

    public HeldOutcomes(JdbcTemplate jdbc, DeliveryReceipts receipts, Clock clock,
                        @Value("${dad-coach.whatsapp.gateway-held-reports:false}") boolean enabled,
                        @Value("${dad-coach.whatsapp.receipts.enabled:true}") boolean receiptsEnabled) {
        this.jdbc = jdbc;
        this.receipts = receipts;
        this.clock = clock;
        this.enabled = enabled;
        this.receiptsEnabled = receiptsEnabled;
    }

    /** {@code GATEWAY_HELD_REPORTS}: off = the endpoint answers 404 and changes nothing (the platform retries). */
    public boolean isEnabled() {
        return enabled;
    }

    /** Tests flip the switch on the one cached context. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static Optional<Outcome> outcome(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Outcome.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public Applied apply(long heldId, Outcome outcome, String wamid, String errorCode, String closedReason,
                         String latestStatus, Instant latestStatusAt, String latestErrorCode, Instant closedAt) {
        String holdId = SharedNumberGate.HELD_PREFIX + heldId;
        boolean known = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM scheduled_response_delivery "
                + "WHERE gateway_hold_id = ?) OR EXISTS (SELECT 1 FROM login_link WHERE gateway_hold_id = ?)",
                Boolean.class, holdId, holdId));
        if (!known) {
            log.atInfo().setMessage("whatsapp.held.outcome").addKeyValue("heldId", heldId).addKeyValue("outcome", outcome)
                    .addKeyValue("known", false).log();
            return new Applied(false, 0, 0, 0);
        }
        Timestamp at = Timestamp.from(closedAt != null ? closedAt : clock.instant());
        String sentId = wamid == null || wamid.isBlank() || wamid.startsWith(SharedNumberGate.HELD_PREFIX) ? null : wamid.trim();
        int scheduled;
        int links;
        int statusMoves = 0;
        switch (outcome) {
            case SENT -> {
                scheduled = jdbc.update("UPDATE scheduled_response_delivery SET status = 'ACCEPTED', provider_message_id = ?, "
                        + "status_at = ? WHERE gateway_hold_id = ? AND status = 'HELD'", sentId, at, holdId);
                links = jdbc.update("UPDATE login_link SET receipt_status = NULL, provider_message_id = ? "
                        + "WHERE gateway_hold_id = ? AND receipt_status = 'HELD'", sentId, holdId);
                // the latest status - also on a later report for the same held id (a newer status is a new report) -
                // only for the wamid this held id was linked to
                if (sentId != null && receiptsEnabled && linkedTo(holdId, sentId)) {
                    if (latestStatus != null) {
                        statusMoves += receipts.applyGatewayStatus(sentId, latestStatus,
                                latestStatusAt != null ? latestStatusAt : at.toInstant(), latestErrorCode);
                    }
                    statusMoves += receipts.applyPending(sentId);
                }
            }
            case FAILED, EXPIRED -> {
                String reason = cut(outcome == Outcome.FAILED
                        ? "GATEWAY_" + (blank(errorCode) ? "SEND_FAILED" : errorCode.trim())
                        : "GATEWAY_HELD_EXPIRED" + (blank(closedReason) ? "" : "_" + closedReason.trim()), 200);
                scheduled = jdbc.update("UPDATE scheduled_response_delivery SET status = 'FAILED', failure_reason = ?, "
                        + "status_at = ? WHERE gateway_hold_id = ? AND status = 'HELD'", reason, at, holdId);
                links = jdbc.update("UPDATE login_link SET delivery_status = 'FAILED', delivery_error = ?, "
                        + "receipt_status = 'FAILED', receipt_at = ? WHERE gateway_hold_id = ? AND receipt_status = 'HELD'",
                        reason, at, holdId);
            }
            case UNKNOWN -> {
                String reason = cut("GATEWAY_UNKNOWN" + (blank(errorCode) ? "" : "_" + errorCode.trim()), 200);
                scheduled = jdbc.update("UPDATE scheduled_response_delivery SET status = 'UNKNOWN', failure_reason = ?, "
                        + "status_at = ? WHERE gateway_hold_id = ? AND status = 'HELD'", reason, at, holdId);
                links = jdbc.update("UPDATE login_link SET receipt_status = 'UNKNOWN', delivery_error = ?, receipt_at = ? "
                        + "WHERE gateway_hold_id = ? AND receipt_status = 'HELD'", reason, at, holdId);
            }
            default -> throw new IllegalStateException("unhandled outcome " + outcome);
        }
        log.atInfo().setMessage("whatsapp.held.outcome").addKeyValue("heldId", heldId).addKeyValue("outcome", outcome)
                .addKeyValue("known", true).addKeyValue("scheduled", scheduled).addKeyValue("links", links)
                .addKeyValue("latestStatus", latestStatus).addKeyValue("statusMoves", statusMoves).log();
        return new Applied(true, scheduled, links, statusMoves);
    }

    private boolean linkedTo(String holdId, String wamid) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM scheduled_response_delivery WHERE "
                + "gateway_hold_id = ? AND provider_message_id = ?) OR EXISTS (SELECT 1 FROM login_link WHERE "
                + "gateway_hold_id = ? AND provider_message_id = ?)", Boolean.class, holdId, wamid, holdId, wamid));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
