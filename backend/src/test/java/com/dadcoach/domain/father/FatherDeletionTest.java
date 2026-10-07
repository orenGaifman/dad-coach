package com.dadcoach.domain.father;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.integration.platform.lifecycle.DeletedSenders;
import com.dadcoach.integration.platform.lifecycle.PlatformPersonDeletions;
import com.dadcoach.support.FakeServers;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Deleting a father for good (person lifecycle): the platform deletes his person through its tenancy API from a
 * durable outbox (register INACTIVE under his ref, delete; retried with backoff), his Dad Coach data is purged -
 * every owned table, his idempotency rows too - and his number reaches no AI until the platform confirms.
 */
class FatherDeletionTest extends AbstractIntegrationTest {

    @Autowired FatherDeletionService deletions;
    @Autowired PlatformPersonDeletions outbox;
    @Autowired DeletedSenders deletedSenders;
    @Autowired MockMvc mvc;

    private Father fatherWithData(String phone) {
        Father f = data.activeFather(phone);
        data.child(f, "נועה", 6);
        data.endpoint(f, true);
        jdbc.update("INSERT INTO tool_idempotency (id, scope, idempotency_key, status, actor_ref, expires_at) "
                + "VALUES (gen_random_uuid(), 'TOOL:add_child', 'k', 'SUCCEEDED', ?, now() + interval '1 day')", "whatsapp:" + phone);
        return f;
    }

    private int rows(String table, Father f) {
        String column = table.equals("communication_endpoints") ? "father_id = '" + new UUID(0L, f.getId()) + "'::uuid"
                : "father_id = " + f.getId();
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column, Integer.class);
    }

    @Test
    void hisOwnRequestDeletesOnThePlatformThenPurgesEverything() {
        Father f = fatherWithData("+19995550500");
        deletions.requestByFather(f);
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE id = ?", String.class, f.getId())).isEqualTo("DELETED");
        assertThat(deletedSenders.isDeleted(f.getPhone())).isTrue();

        assertThat(outbox.sendDue()).isEqualTo(1);
        assertThat(fake.calls("/api/v1/tenancy/people")).hasSize(1);
        assertThat(fake.calls("/api/v1/tenancy/people").get(0).body()).contains("INACTIVE").contains(new UUID(0L, f.getId()).toString());
        assertThat(fake.calls("/api/v1/tenancy/tenants/")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM father", Integer.class)).isZero();
        assertThat(rows("child", f)).isZero();
        assertThat(rows("communication_endpoints", f)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tool_idempotency", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT outcome FROM platform_person_deletion", String.class)).isEqualTo("DELETED");
        assertThat(deletedSenders.isDeleted(f.getPhone())).isFalse(); // confirmed: the number may start over

        assertThat(outbox.sendDue()).isZero(); // idempotent
    }

    @Test
    void midOnboardingHeCanAskToo() {
        Father f = data.father("+19995550501", null, FatherStatus.ONBOARDING);
        deletions.requestByFather(f);
        assertThat(jdbc.queryForObject("SELECT status FROM father WHERE id = ?", String.class, f.getId())).isEqualTo("DELETED");
    }

    @Test
    void anOperatorDeletePurgesAtOnceAndThePlatformOutageIsRetriedNotLost() throws Exception {
        Father f = fatherWithData("+19995550502");
        fake.onTenancy(c -> new FakeServers.Reply(503, "{\"code\":\"down\"}"));
        mvc.perform(delete("/api/v1/admin/fathers/" + new UUID(0L, f.getId())).header("X-API-Key", ADMIN_KEY))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM father", Integer.class)).isZero();
        assertThat(deletedSenders.isDeleted(f.getPhone())).isTrue();

        assertThat(outbox.sendDue()).isZero();
        assertThat(jdbc.queryForObject("SELECT attempts FROM platform_person_deletion", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_error FROM platform_person_deletion", String.class)).startsWith("503");

        fake.reset();
        clock.advance(Duration.ofMinutes(2));
        jdbc.update("UPDATE platform_person_deletion SET next_attempt_at = now() - interval '1 minute'");
        assertThat(outbox.sendDue()).isEqualTo(1);
        assertThat(deletedSenders.isDeleted(f.getPhone())).isFalse();
    }
}
