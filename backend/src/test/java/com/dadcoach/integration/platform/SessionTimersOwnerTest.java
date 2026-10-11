package com.dadcoach.integration.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.support.FakeServers;
import com.dadcoach.web.father.SessionsService;
import com.dadcoach.weeklyplan.SessionTimerPlanner;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * D-050 (Unified Workflow Phase 5.4): with {@code DC_SESSION_TIMERS_PRODUCT_OWNED} on, Dad Coach owns the session timers -
 * the reconcile arms the missing ones of upcoming sessions (up to the follow-up time), leaves the present ones, and a
 * session closed on the dashboard has its timers cancelled; off, nothing of this runs. The fake platform keeps the
 * timers in memory (GET / POST / DELETE of /api/v1/worker/scheduled-transitions). The clock: Tuesday 3.11 12:00 Israel.
 */
class SessionTimersOwnerTest extends AbstractIntegrationTest {

    static final String PHONE = "+19995550750";
    static final String USER = "whatsapp:" + PHONE;

    @Autowired WorkflowPlatformProperties properties;
    @Autowired SessionTimersOwner owner;
    @Autowired SessionsService sessionsService;

    @AfterEach
    void off() {
        properties.setSessionTimersProductOwned(false);
    }

    private Father father() {
        Father f = data.father(PHONE, "אורן", com.dadcoach.father.FatherStatus.ACTIVE);
        data.endpoint(f, true);
        return f;
    }

    /** A SCHEDULED session starting {@code startInMinutes} from the pinned now, one hour long. */
    private UUID session(Father f, Child child, long startInMinutes) {
        Instant start = clock.instant().truncatedTo(ChronoUnit.MINUTES).plus(startInMinutes, ChronoUnit.MINUTES);
        return jdbc.queryForObject("INSERT INTO quality_time (father_id, child_id, scheduled_start, scheduled_end, status) "
                        + "VALUES (?, ?, ?, ?, 'SCHEDULED') RETURNING id", UUID.class, f.getId(), child.getId(),
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(start.plus(60, ChronoUnit.MINUTES)));
    }

    private long pending(UUID session) {
        return FakeServers.INSTANCE.pendingTimers().stream().filter(t -> session.toString().equals(t.referenceId())).count();
    }

    @Test
    @DisplayName("on: the reconcile arms the missing timers of an upcoming session; a second pass arms nothing more")
    void reconcileArmsWhatIsMissing() {
        properties.setSessionTimersProductOwned(true);
        Father f = father();
        Child noa = data.child(f, "נועה", 6);
        UUID tomorrow = session(f, noa, 24 * 60 + 6 * 60);          // Wednesday 18:00: morning 08:00, 17:00, 19:30

        assertThat(owner.reconcile()).isEqualTo(1);
        assertThat(pending(tomorrow)).isEqualTo(3);
        int posts = FakeServers.INSTANCE.scheduledTransitionCalls("POST").size();

        owner.reconcile();
        assertThat(FakeServers.INSTANCE.scheduledTransitionCalls("POST")).as("present timers are left").hasSize(posts);
        assertThat(pending(tomorrow)).isEqualTo(3);
    }

    @Test
    @DisplayName("on: a session that just ended still gets its missing follow-up (until end + 30 min); a later one does not")
    void followUpAfterTheEnd() {
        properties.setSessionTimersProductOwned(true);
        Father f = father();
        Child noa = data.child(f, "נועה", 6);
        UUID justEnded = session(f, noa, -70);                       // ended 10 min ago: follow-up in 20 min
        UUID longGone = session(f, noa, -120);                       // ended 60 min ago: nothing left to arm

        owner.reconcile();

        assertThat(FakeServers.INSTANCE.pendingTimers().stream().filter(t -> justEnded.toString().equals(t.referenceId()))
                .map(FakeServers.Timer::transitionKey)).containsExactly(SessionTimerPlanner.FOLLOW_UP);
        assertThat(pending(longGone)).isZero();
    }

    @Test
    @DisplayName("on: cancelling a session on the dashboard cancels its timers after the change commits")
    void dashboardCancelCancelsTheTimers() {
        properties.setSessionTimersProductOwned(true);
        Father f = father();
        Child noa = data.child(f, "נועה", 6);
        UUID tomorrow = session(f, noa, 24 * 60 + 6 * 60);
        owner.reconcile();
        assertThat(pending(tomorrow)).isEqualTo(3);

        sessionsService.cancel(f, tomorrow);

        assertThat(pending(tomorrow)).isZero();
        assertThat(FakeServers.INSTANCE.scheduledTransitionCalls("DELETE")).isNotEmpty();
    }

    @Test
    @DisplayName("off (the default): the dashboard cancel calls nothing, the scheduled reconcile does nothing")
    void offIsToday() {
        Father f = father();
        Child noa = data.child(f, "נועה", 6);
        UUID tomorrow = session(f, noa, 24 * 60 + 6 * 60);

        owner.scheduledReconcile();
        sessionsService.cancel(f, tomorrow);

        assertThat(FakeServers.INSTANCE.scheduledTransitionCalls()).isEmpty();
    }

    @Test
    @DisplayName("on: the platform down - the reconcile and the cancel never throw")
    void platformDown() {
        properties.setSessionTimersProductOwned(true);
        FakeServers.INSTANCE.onScheduledTransitions(call -> new FakeServers.Reply(503, "{}"));
        Father f = father();
        Child noa = data.child(f, "נועה", 6);
        UUID tomorrow = session(f, noa, 24 * 60 + 6 * 60);

        assertThatCode(() -> owner.scheduledReconcile()).doesNotThrowAnyException();
        assertThatCode(() -> sessionsService.cancel(f, tomorrow)).doesNotThrowAnyException();
    }
}
