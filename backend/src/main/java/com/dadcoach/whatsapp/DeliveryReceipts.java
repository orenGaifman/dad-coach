package com.dadcoach.whatsapp;

import com.dadcoach.channel.delivery.DeliveryStatus;
import com.dadcoach.channel.dto.StatusUpdateDto;
import com.dadcoach.integration.platform.SharedNumberGate;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DeliveryReceipts(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Applies one receipt; returns how many recorded messages it moved (0: unknown wamid, stale or ignored). */
    @Transactional
    public int apply(StatusUpdateDto receipt) {
        String wamid = receipt.providerMessageId();
        DeliveryStatus to = receipt.status() == null ? null : switch (receipt.status().toLowerCase(Locale.ROOT)) {
            case "sent" -> DeliveryStatus.SENT;
            case "delivered" -> DeliveryStatus.DELIVERED;
            case "read" -> DeliveryStatus.READ;
            case "failed" -> DeliveryStatus.FAILED;
            default -> null; // e.g. "deleted", "warning": nothing to record
        };
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
