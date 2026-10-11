package com.dadcoach.platformjobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unified Workflow 5.4 v3 (PHASE5_4_SPEC.md §v3.3, §v3.8): Dad Coach's side of the platform's product jobs - who owns a
 * job, the slot ledger (DONE answers its first result, a dead holder's RUNNING row is taken over), the advisory locks
 * (a busy job answers BUSY, a tick that finds the lock taken is skipped, a sending job waits for the messaging lane), a
 * failure that reaches the platform, and the registration body.
 */
@DisplayName("5.4 v3: Dad Coach platform jobs")
class PlatformJobsTest extends AbstractIntegrationTest {

    @Autowired PlatformJobs jobs;
    @Autowired PlatformJobsSync sync;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    private final AtomicInteger counter = new AtomicInteger();

    @AfterEach
    void reset() {
        jobs.setPlatformOwned(Set.of());
        jobs.setLaneWait(PlatformJobs.LANE_WAIT);
        jobs.probe().orElseThrow().configure(0, 0);
    }

    private PlatformJobs.Job counting(String key, boolean sends) {
        PlatformJobs.Job job = PlatformJobs.Job.every(key, Duration.ofMinutes(5), Duration.ofMinutes(1), sends, "test",
                () -> Map.of("n", counter.incrementAndGet()));
        jobs.register(job);
        return job;
    }

    private static Instant slot(int n) {
        return Instant.parse("2026-11-03T10:00:00Z").plusSeconds(300L * n);
    }

    @Test
    @DisplayName("a job the platform does not own answers PRODUCT_OWNED and does not run; an unknown job is 404")
    void productOwned() {
        counting("t_owned", false);
        assertThat(jobs.runForPlatform("t_owned", slot(1)).body()).containsEntry("status", "PRODUCT_OWNED");
        assertThat(counter.get()).isZero();
        assertThat(jobs.runForPlatform("t_nothing", slot(1)).httpStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("one run per slot: the same slot again answers DUPLICATE with the first result; the next slot runs")
    void oneRunPerSlot() {
        counting("t_slot", false);
        jobs.setPlatformOwned(Set.of("t_slot"));
        PlatformJobs.Outcome first = jobs.runForPlatform("t_slot", slot(2));
        PlatformJobs.Outcome again = jobs.runForPlatform("t_slot", slot(2));
        assertThat(first.httpStatus()).isEqualTo(200);
        assertThat(first.body()).containsEntry("status", "DONE");
        assertThat(again.body()).containsEntry("status", "DUPLICATE").containsEntry("result", Map.of("n", 1));
        assertThat(counter.get()).isEqualTo(1);
        jobs.runForPlatform("t_slot", slot(3));
        assertThat(counter.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_job_run WHERE job_key = 't_slot'", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("a failing run answers 500 and leaves no row: the platform's retry of the slot runs it")
    void failureIsRetried() {
        jobs.setPlatformOwned(Set.of(QaProbe.KEY));
        jobs.probe().orElseThrow().configure(0, 1);
        assertThat(jobs.runForPlatform(QaProbe.KEY, slot(4)).httpStatus()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_job_run WHERE job_key = ? AND slot = ?", Integer.class,
                QaProbe.KEY, Timestamp.from(slot(4)))).isZero();
        PlatformJobs.Outcome retry = jobs.runForPlatform(QaProbe.KEY, slot(4));
        assertThat(retry.body()).containsEntry("status", "DONE");
    }

    @Test
    @DisplayName("a job running elsewhere (its lock held - another instance, a tick) answers BUSY; a tick is skipped")
    void busyWhileLocked() throws Exception {
        counting("t_busy", false);
        jobs.setPlatformOwned(Set.of("t_busy"));
        try (Connection other = dataSource.getConnection()) {
            lock(other, PlatformJobs.LOCK_PREFIX + "t_busy");
            assertThat(jobs.runForPlatform("t_busy", slot(5)).httpStatus()).isEqualTo(409);
            jobs.setPlatformOwned(Set.of());
            jobs.tick("t_busy");
            assertThat(counter.get()).as("the tick is skipped too").isZero();
            unlock(other, PlatformJobs.LOCK_PREFIX + "t_busy");
        }
        jobs.tick("t_busy");
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a slot left RUNNING by a dead process (its lock free) is taken over and run once")
    void deadHolderTakenOver() {
        counting("t_dead", false);
        jobs.setPlatformOwned(Set.of("t_dead"));
        jdbc.update("INSERT INTO platform_job_run (job_key, slot, status, started_at) VALUES ('t_dead', ?, 'RUNNING', now() - interval '1 minute')",
                Timestamp.from(slot(6)));
        assertThat(jobs.runForPlatform("t_dead", slot(6)).body()).containsEntry("status", "DONE");
        assertThat(counter.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_job_run WHERE job_key = 't_dead'", String.class)).isEqualTo("DONE");
    }

    @Test
    @DisplayName("a sending job waits for the messaging lane: with the lane held elsewhere it answers BUSY after the wait")
    void sendingJobsShareTheLane() throws Exception {
        counting("t_lane", true);
        jobs.setPlatformOwned(Set.of("t_lane"));
        jobs.setLaneWait(Duration.ofMillis(500));
        try (Connection other = dataSource.getConnection()) {
            lock(other, PlatformJobs.LANE_LOCK);
            assertThat(jobs.runForPlatform("t_lane", slot(7)).httpStatus()).isEqualTo(409);
            unlock(other, PlatformJobs.LANE_LOCK);
        }
        assertThat(jobs.runForPlatform("t_lane", slot(7)).body()).containsEntry("status", "DONE");
    }

    @Test
    @DisplayName("a PLATFORM job's tick does nothing; a PRODUCT job's tick runs it (under its lock)")
    void tickByOwner() {
        counting("t_tick", false);
        jobs.setPlatformOwned(Set.of("t_tick"));
        jobs.tick("t_tick");
        assertThat(counter.get()).isZero();
        jobs.setPlatformOwned(Set.of());
        jobs.tick("t_tick");
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("the registration lists every Dad Coach job, enabled only when the platform owns it")
    @SuppressWarnings("unchecked")
    void registrationBody() {
        jobs.setPlatformOwned(Set.of("weekly_goals"));
        Map<String, Object> body = sync.body();
        List<Map<String, Object>> list = (List<Map<String, Object>>) body.get("jobs");
        assertThat(list).extracting(m -> m.get("jobKey")).contains("weekly_goals", "session_timers", "person_deletions", "qa_probe");
        assertThat(list).filteredOn(m -> "weekly_goals".equals(m.get("jobKey"))).singleElement()
                .satisfies(m -> assertThat(m).containsEntry("enabled", true).containsEntry("every", "PT1H"));
        assertThat(list).filteredOn(m -> "session_timers".equals(m.get("jobKey"))).singleElement()
                .satisfies(m -> assertThat(m).containsEntry("enabled", false));
    }

    private static void lock(Connection c, String name) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_lock(hashtextextended(?, 0))")) {
            ps.setString(1, name);
            ps.executeQuery().close();
        }
    }

    private static void unlock(Connection c, String name) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 0))")) {
            ps.setString(1, name);
            ps.executeQuery().close();
        }
    }
}
