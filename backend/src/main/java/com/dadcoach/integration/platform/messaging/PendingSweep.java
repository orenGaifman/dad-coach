package com.dadcoach.integration.platform.messaging;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * D-053 (Unified Workflow 6.4, spec v2 R12): once at startup, the ledger rows still pending with no platform message
 * after {@link #OLDER_THAN} are marked UNKNOWN - rows a direct send (before D-053) or the shared-number gateway's hold
 * left open, whose end nothing reports any more (Meta's receipts and the gateway's held reports went with D-053). A row
 * the reconcile job still drives ({@code platform_next_at} set: ambiguous, not created yet, a lease) is left to it.
 * UNKNOWN is never failed and never sent again (D-042). The counts are logged ({@code messaging.pending_swept}).
 */
@Component
public class PendingSweep {

    private static final Logger log = LoggerFactory.getLogger(PendingSweep.class);
    static final Duration OLDER_THAN = Duration.ofHours(1);
    static final String REASON = "SWEPT_NO_PLATFORM_MESSAGE";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PendingSweep(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void atStartup() {
        try {
            sweep();
        } catch (RuntimeException e) {
            log.atWarn().setMessage("messaging.pending_sweep_failed").addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }

    /** One sweep; returns the rows marked UNKNOWN (scheduled messages, links). */
    public int[] sweep() {
        Instant now = clock.instant();
        Timestamp at = Timestamp.from(now);
        Timestamp before = Timestamp.from(now.minus(OLDER_THAN));
        int scheduled = jdbc.update("UPDATE scheduled_response_delivery SET status = 'UNKNOWN', failure_reason = ?, "
                + "status_at = ?, completed_at = COALESCE(completed_at, ?), platform_request = NULL, "
                + "platform_state = CASE WHEN platform_state IS NULL THEN NULL ELSE 'GAVE_UP' END "
                + "WHERE status IN ('SENDING', 'HELD') AND platform_message_id IS NULL AND platform_next_at IS NULL "
                + "AND created_at < ?", REASON, at, at, before);
        int links = jdbc.update("UPDATE login_link SET delivery_status = 'SENT', receipt_status = 'UNKNOWN', receipt_at = ?, "
                + "delivery_error = ?, platform_request = NULL, "
                + "platform_state = CASE WHEN platform_state IS NULL THEN NULL ELSE 'GAVE_UP' END "
                + "WHERE platform_message_id IS NULL AND platform_next_at IS NULL AND created_at < ? "
                + "AND ((delivery_status = 'SENT' AND receipt_status IN ('QUEUED', 'HELD')) "
                + "OR (delivery_status = 'ISSUED' AND platform_state IN ('POSTING', 'AMBIGUOUS', 'NOT_CREATED', 'DIRECT')))",
                at, REASON, before);
        log.atInfo().setMessage("messaging.pending_swept").addKeyValue("scheduled", scheduled).addKeyValue("links", links).log();
        return new int[] {scheduled, links};
    }
}
