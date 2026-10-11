package com.dadcoach.whatsapp;

import com.dadcoach.channel.delivery.DeliveryStatus;
import com.dadcoach.channel.dto.StatusUpdateDto;
import com.dadcoach.config.SchedulingLanes;
import com.dadcoach.integration.platform.GatewayHeldReports;
import com.dadcoach.integration.platform.SharedNumberGate;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meta's status receipts for the messages Dad Coach records (D-038, DC-B2): a scheduled coach message
 * ({@code scheduled_response_delivery}) and a dashboard link ({@code login_link}), matched on the wamid kept when Meta
 * accepted the send (DC-B1). A receipt only moves a message forward - ACCEPTED &lt; SENT &lt; DELIVERED &lt; READ, or
 * FAILED before it was delivered - with one conditional UPDATE per table, so two webhooks racing (Meta sends
 * "delivered" and "read" close together, in any order) never move it back. FAILED is final. A wamid Dad Coach never
 * recorded (a conversational reply, someone else's message) matches nothing and changes nothing; a gateway hold id
 * ({@code held:<n>}) is never a wamid.
 *
 * <p>Meta can answer a send with "failed" (e.g. 131047) within milliseconds - before the row holding the wamid is
 * committed - and a message the shared-number gateway held and later sent gets its receipts before the gateway's report
 * tells Dad Coach its wamid (D-042). A receipt for a wamid no row holds is therefore kept in memory for
 * {@link #PENDING_FOR} and re-applied every 15 seconds until its row appears, or at once by {@link #applyPending}
 * (at most {@link #PENDING_MAX} wamids; lost on a restart). With {@code GATEWAY_HELD_REPORTS} off only "failed" is
 * kept, exactly as before D-042; on, every status (one receipt per status per wamid).
 */
@Component
public class DeliveryReceipts {

    private static final Logger log = LoggerFactory.getLogger(DeliveryReceipts.class);

    /** The statuses each receipt may move a scheduled message from (forward only). */
    private static final Map<DeliveryStatus, List<String>> SCHEDULED_FROM = Map.of(
            DeliveryStatus.SENT, List.of("ACCEPTED"),
            DeliveryStatus.DELIVERED, List.of("ACCEPTED", "SENT"),
            DeliveryStatus.READ, List.of("ACCEPTED", "SENT", "DELIVERED"),
            DeliveryStatus.FAILED, List.of("ACCEPTED", "SENT"));

    /** The same for a link's receipt_status, where NULL means "accepted by Meta". */
    private static final Map<DeliveryStatus, List<String>> LINK_FROM = Map.of(
            DeliveryStatus.SENT, List.of(),
            DeliveryStatus.DELIVERED, List.of("SENT"),
            DeliveryStatus.READ, List.of("SENT", "DELIVERED"),
            DeliveryStatus.FAILED, List.of("SENT"));

    static final Duration PENDING_FOR = Duration.ofMinutes(2);
    static final int PENDING_MAX = 500;

    /** The order a wamid's kept receipts are re-applied in (forward; "failed" last, so it never jumps a delivery). */
    private static final List<DeliveryStatus> REPLAY_ORDER =
            List.of(DeliveryStatus.SENT, DeliveryStatus.DELIVERED, DeliveryStatus.READ, DeliveryStatus.FAILED);

    /** The receipts kept for one unknown wamid: the latest of each status, until {@code until}. */
    private record Pending(Map<DeliveryStatus, StatusUpdateDto> receipts, Instant until) {}

    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final GatewayHeldReports heldReports;

    public DeliveryReceipts(JdbcTemplate jdbc, Clock clock, GatewayHeldReports heldReports) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.heldReports = heldReports;
    }

    /** Applies one receipt; returns how many recorded messages it moved (0: unknown wamid, stale or ignored). */
    @Transactional
    public int apply(StatusUpdateDto receipt) {
        int moved = move(receipt);
        // D-042: with GATEWAY_HELD_REPORTS on, a receipt of any status is kept; off, only "failed" (as before D-042)
        boolean keepable = heldReports.isEnabled() ? statusOfReceipt(receipt) != null
                : "failed".equalsIgnoreCase(receipt.status());
        if (moved == 0 && keepable && receipt.providerMessageId() != null
                && !receipt.providerMessageId().startsWith(SharedNumberGate.HELD_PREFIX)
                && !recorded(receipt.providerMessageId())) {
            remember(receipt);
        }
        return moved;
    }

    /**
     * D-042: the latest status the shared-number gateway saw for a message it held and then sent (the platform's
     * timeline vocabulary: ACCEPTED = Meta's "sent", DELIVERED, READ, FAILED), applied like Meta's receipt - forward only.
     * Returns how many recorded messages it moved.
     */
    @Transactional
    public int applyGatewayStatus(String wamid, String platformStatus, Instant at, String errorCode) {
        if (wamid == null || platformStatus == null) {
            return 0;
        }
        String meta = switch (platformStatus.trim().toUpperCase(Locale.ROOT)) {
            case "ACCEPTED" -> "sent";
            case "DELIVERED" -> "delivered";
            case "READ" -> "read";
            case "FAILED" -> "failed";
            default -> null;
        };
        if (meta == null) {
            return 0;
        }
        Integer code = errorCode != null && errorCode.matches("\\d{1,9}") ? Integer.valueOf(errorCode) : null;
        String message = errorCode != null && code == null ? errorCode : null;
        return move(new StatusUpdateDto(wamid, meta, null, at, code, message));
    }

    /**
     * D-042: applies at once the receipts kept for a wamid that now has its row (a held message the gateway sent: its
     * receipts can beat the report). Returns how many moves they made.
     */
    @Transactional
    public int applyPending(String wamid) {
        Pending kept = wamid == null ? null : pending.remove(wamid);
        if (kept == null) {
            return 0;
        }
        return replay(kept);
    }

    /**
     * Re-applies the receipts that came before their row was committed; drops a wamid's receipts once its row exists
     * (applied, moved or not) or their time is up.
     */
    @Scheduled(fixedDelayString = "PT15S", initialDelayString = "PT15S", scheduler = SchedulingLanes.HOUSEKEEPING)
    public void retryPending() {
        Instant now = clock.instant();
        for (Map.Entry<String, Pending> e : pending.entrySet()) {
            if (now.isAfter(e.getValue().until())) {
                pending.remove(e.getKey(), e.getValue());
                continue;
            }
            try {
                if (!heldReports.isEnabled()) {
                    // as before D-042: re-apply, drop once it moved or its row exists
                    if (replay(e.getValue()) > 0 || recorded(e.getKey())) {
                        pending.remove(e.getKey(), e.getValue());
                    }
                } else if (recorded(e.getKey()) && pending.remove(e.getKey(), e.getValue())) {
                    replay(e.getValue());
                }
            } catch (RuntimeException failure) {
                log.atWarn().setMessage("whatsapp.receipt.retry_failed").addKeyValue("error", failure.getClass().getSimpleName()).log();
            }
        }
    }

    /** Test isolation: wamids repeat across tests. */
    public void forgetPending() {
        pending.clear();
    }

    private int replay(Pending kept) {
        int moved = 0;
        for (DeliveryStatus status : REPLAY_ORDER) {
            StatusUpdateDto receipt = kept.receipts().get(status);
            if (receipt != null) {
                moved += move(receipt);
            }
        }
        return moved;
    }

    private void remember(StatusUpdateDto receipt) {
        String wamid = receipt.providerMessageId();
        DeliveryStatus status = statusOfReceipt(receipt);
        if (!heldReports.isEnabled()) {
            // as before D-042: one "failed" receipt per wamid, a full buffer takes nothing
            if (pending.size() >= PENDING_MAX) {
                log.atWarn().setMessage("whatsapp.receipt.pending_full").log();
                return;
            }
            pending.put(wamid, new Pending(Map.of(status, receipt), clock.instant().plus(PENDING_FOR)));
            return;
        }
        if (!pending.containsKey(wamid) && pending.size() >= PENDING_MAX) {
            log.atWarn().setMessage("whatsapp.receipt.pending_full").log();
            return;
        }
        pending.compute(wamid, (k, kept) -> {
            Map<DeliveryStatus, StatusUpdateDto> receipts = new java.util.EnumMap<>(DeliveryStatus.class);
            if (kept != null) {
                receipts.putAll(kept.receipts());
            }
            receipts.put(status, receipt);
            return new Pending(receipts, kept != null ? kept.until() : clock.instant().plus(PENDING_FOR));
        });
    }

    /** Meta's receipt status as one Dad Coach records; null for one it does not (e.g. "deleted", "warning"). */
    private static DeliveryStatus statusOfReceipt(StatusUpdateDto receipt) {
        return receipt.status() == null ? null : switch (receipt.status().toLowerCase(Locale.ROOT)) {
            case "sent" -> DeliveryStatus.SENT;
            case "delivered" -> DeliveryStatus.DELIVERED;
            case "read" -> DeliveryStatus.READ;
            case "failed" -> DeliveryStatus.FAILED;
            default -> null;
        };
    }

    private boolean recorded(String wamid) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM scheduled_response_delivery WHERE "
                + "provider_message_id = ?) OR EXISTS (SELECT 1 FROM login_link WHERE provider_message_id = ?)",
                Boolean.class, wamid, wamid));
    }

    private int move(StatusUpdateDto receipt) {
        String wamid = receipt.providerMessageId();
        DeliveryStatus to = statusOfReceipt(receipt); // e.g. "deleted", "warning": nothing to record
        if (wamid == null || wamid.startsWith(SharedNumberGate.HELD_PREFIX) || to == null) {
            return 0;
        }
        Timestamp at = Timestamp.from(receipt.timestamp() != null ? receipt.timestamp() : clock.instant());
        int moved;
        if (to == DeliveryStatus.FAILED) {
            String reason = "META_" + (receipt.errorCode() == null ? "UNKNOWN" : receipt.errorCode())
                    + (receipt.errorMessage() == null ? "" : ": " + receipt.errorMessage());
            moved = jdbc.update("UPDATE scheduled_response_delivery SET status = 'FAILED', failure_reason = ?, status_at = ? "
                    + "WHERE provider_message_id = ? AND status IN (" + in(SCHEDULED_FROM.get(to)) + ")", reason, at, wamid);
            moved += jdbc.update("UPDATE login_link SET receipt_status = 'FAILED', receipt_at = ?, delivery_status = 'FAILED', "
                    + "delivery_error = ? WHERE provider_message_id = ? AND delivery_status = 'SENT' AND "
                    + linkFrom(to), at, reason.substring(0, Math.min(reason.length(), 200)), wamid);
        } else {
            moved = jdbc.update("UPDATE scheduled_response_delivery SET status = ?, status_at = ? "
                    + "WHERE provider_message_id = ? AND status IN (" + in(SCHEDULED_FROM.get(to)) + ")", to.name(), at, wamid);
            moved += jdbc.update("UPDATE login_link SET receipt_status = ?, receipt_at = ? WHERE provider_message_id = ? AND "
                    + linkFrom(to), to.name(), at, wamid);
        }
        log.atInfo().setMessage("whatsapp.receipt").addKeyValue("status", to).addKeyValue("moved", moved)
                .addKeyValue("errorCode", receipt.errorCode()).log();
        return moved;
    }

    /**
     * What Meta last said about a message Dad Coach recorded: SENT once accepted (or Meta's "sent"), then DELIVERED,
     * READ or FAILED; PENDING when it is held by the gateway, not sent yet, or not a message Dad Coach records.
     */
    @Transactional(readOnly = true)
    public DeliveryStatus statusOf(String providerMessageId) {
        if (providerMessageId == null || providerMessageId.startsWith(SharedNumberGate.HELD_PREFIX)) {
            return DeliveryStatus.PENDING;
        }
        List<String> scheduled = jdbc.queryForList("SELECT status FROM scheduled_response_delivery WHERE provider_message_id = ? "
                + "ORDER BY created_at DESC LIMIT 1", String.class, providerMessageId);
        if (!scheduled.isEmpty()) {
            return switch (scheduled.get(0)) {
                case "ACCEPTED", "SENT" -> DeliveryStatus.SENT;
                case "DELIVERED" -> DeliveryStatus.DELIVERED;
                case "READ" -> DeliveryStatus.READ;
                case "FAILED" -> DeliveryStatus.FAILED;
                default -> DeliveryStatus.PENDING;
            };
        }
        List<Map<String, Object>> links = jdbc.queryForList("SELECT delivery_status, receipt_status FROM login_link "
                + "WHERE provider_message_id = ? ORDER BY created_at DESC LIMIT 1", providerMessageId);
        if (!links.isEmpty()) {
            Object receipt = links.get(0).get("receipt_status");
            if (receipt == null) {
                return "SENT".equals(links.get(0).get("delivery_status")) ? DeliveryStatus.SENT : DeliveryStatus.PENDING;
            }
            return switch ((String) receipt) {
                case "SENT" -> DeliveryStatus.SENT;
                case "DELIVERED" -> DeliveryStatus.DELIVERED;
                case "READ" -> DeliveryStatus.READ;
                case "FAILED" -> DeliveryStatus.FAILED;
                default -> DeliveryStatus.PENDING;
            };
        }
        return DeliveryStatus.PENDING;
    }

    private static String linkFrom(DeliveryStatus to) {
        List<String> from = LINK_FROM.get(to);
        return from.isEmpty() ? "receipt_status IS NULL" : "(receipt_status IS NULL OR receipt_status IN (" + in(from) + "))";
    }

    /** A SQL list of this class's own status constants (never outside input). */
    private static String in(List<String> statuses) {
        return statuses.stream().map(s -> "'" + s + "'").collect(Collectors.joining(", "));
    }
}
