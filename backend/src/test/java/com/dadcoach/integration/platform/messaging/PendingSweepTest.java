package com.dadcoach.integration.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * D-053 (spec v2 R12): the one-time startup sweep - rows left pending by a direct send or a gateway hold before D-053,
 * with no platform message and older than an hour, become UNKNOWN (never failed, never sent again); rows the reconcile
 * job still drives, rows the platform owns and young rows are left alone.
 */
class PendingSweepTest extends AbstractIntegrationTest {

    @Autowired PendingSweep sweep;

    private UUID scheduled(Father f, String trigger, String status, String platformState, Instant created, Instant nextAt,
                           String platformMessageId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO scheduled_response_delivery (id, idempotency_key, trigger_id, father_id, status, created_at, "
                + "platform_state, platform_next_at, platform_message_id, platform_posts) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                id, "scheduled-response:" + trigger, trigger, f.getId(), status, Timestamp.from(created), platformState,
                nextAt == null ? null : Timestamp.from(nextAt), platformMessageId);
        return id;
    }

    private UUID link(Father f, String delivery, String receipt, String platformState, Instant created) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO login_link (id, father_id, token_hash, created_at, expires_at, use_count, delivery_status, "
                + "receipt_status, platform_state, platform_posts) VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, 0)", id, f.getId(),
                UUID.randomUUID().toString().replace("-", ""), Timestamp.from(created),
                Timestamp.from(created.plus(Duration.ofDays(365))), delivery, receipt, platformState);
        return id;
    }

    private Map<String, Object> scheduledRow(UUID id) {
        return jdbc.queryForMap("SELECT status, failure_reason, platform_state FROM scheduled_response_delivery WHERE id = ?", id);
    }

    private Map<String, Object> linkRow(UUID id) {
        return jdbc.queryForMap("SELECT delivery_status, receipt_status, delivery_error FROM login_link WHERE id = ?", id);
    }

    @Test
    @DisplayName("old pending rows with no platform message become UNKNOWN (counted); driven, owned and young rows stay")
    void sweepsOnlyTheAbandoned() {
        Father f = data.activeFather("+19995551001");
        Instant old = clock.instant().minus(Duration.ofHours(3));
        Instant young = clock.instant().minus(Duration.ofMinutes(20));
        UUID directSending = scheduled(f, "s1", "SENDING", null, old, null, null);
        UUID gatewayHeld = scheduled(f, "s2", "HELD", "DIRECT", old, null, null);
        UUID notCreated = scheduled(f, "s3", "SENDING", "NOT_CREATED", old, clock.instant().plus(Duration.ofMinutes(1)), null);
        UUID owned = scheduled(f, "s4", "SENDING", "OWNED", old, null, UUID.randomUUID().toString());
        UUID fresh = scheduled(f, "s5", "SENDING", null, young, null, null);
        UUID accepted = scheduled(f, "s6", "ACCEPTED", null, old, null, null);

        UUID queuedLink = link(f, "SENT", "QUEUED", null, old);
        UUID issuedNeverSent = link(f, "ISSUED", null, null, old);       // the ops API's link: never sent, stays ISSUED
        UUID postingLink = link(f, "ISSUED", null, "POSTING", old);

        int[] swept = sweep.sweep();

        assertThat(swept).containsExactly(2, 2);
        assertThat(scheduledRow(directSending)).containsEntry("status", "UNKNOWN")
                .containsEntry("failure_reason", "SWEPT_NO_PLATFORM_MESSAGE").containsEntry("platform_state", null);
        assertThat(scheduledRow(gatewayHeld)).containsEntry("status", "UNKNOWN").containsEntry("platform_state", "GAVE_UP");
        assertThat(scheduledRow(notCreated)).as("the reconcile job drives it").containsEntry("status", "SENDING");
        assertThat(scheduledRow(owned)).containsEntry("status", "SENDING");
        assertThat(scheduledRow(fresh)).containsEntry("status", "SENDING");
        assertThat(scheduledRow(accepted)).containsEntry("status", "ACCEPTED");
        assertThat(linkRow(queuedLink)).containsEntry("receipt_status", "UNKNOWN").containsEntry("delivery_status", "SENT");
        assertThat(linkRow(issuedNeverSent)).containsEntry("delivery_status", "ISSUED").containsEntry("receipt_status", null);
        assertThat(linkRow(postingLink)).containsEntry("delivery_status", "SENT").containsEntry("receipt_status", "UNKNOWN");

        assertThat(sweep.sweep()).as("idempotent").containsExactly(0, 0);
        assertThat(fake.platformSends()).as("a swept row is never sent").isEmpty();
    }
}
