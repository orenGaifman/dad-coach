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
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    @Autowired com.dadcoach.weeklygoal.WeeklyGoalCompletionJob weeklyGoalJob;
    @Autowired com.dadcoach.integration.platform.SessionTimersOwner sessionTimersOwner;
    @Autowired com.dadcoach.integration.platform.lifecycle.PlatformPersonDeletions personDeletions;

    private final AtomicInteger counter = new AtomicInteger();

    @AfterEach
    void reset() {
        jobs.jobs().stream().map(PlatformJobs.Job::key).filter(k -> k.startsWith("t_")).forEach(jobs::unregister);
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
            jobs.tick("t_busy", counter::incrementAndGet);
            assertThat(counter.get()).as("the tick is skipped too").isZero();
            unlock(other, PlatformJobs.LOCK_PREFIX + "t_busy");
        }
        jobs.tick("t_busy", counter::incrementAndGet);
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
        jobs.tick("t_tick", counter::incrementAndGet);
        assertThat(counter.get()).isZero();
        jobs.setPlatformOwned(Set.of());
        jobs.tick("t_tick", counter::incrementAndGet);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("the registration lists every Dad Coach job, enabled only when the platform owns it")
    @SuppressWarnings("unchecked")
    void registrationBody() {
        jobs.setPlatformOwned(Set.of("session_timers"));
        Map<String, Object> body = sync.body();
        List<Map<String, Object>> list = (List<Map<String, Object>>) body.get("jobs");
        assertThat(list).extracting(m -> m.get("jobKey")).contains("session_timers", "person_deletions", "qa_probe")
                .as("the tests switch the weekly cron off (\"-\"): nothing to register").doesNotContain("weekly_goals");
        assertThat(list).filteredOn(m -> "session_timers".equals(m.get("jobKey"))).singleElement()
                .satisfies(m -> assertThat(m).containsEntry("enabled", true).containsEntry("every", "PT10M"));
        assertThat(list).filteredOn(m -> "person_deletions".equals(m.get("jobKey"))).singleElement()
                .satisfies(m -> assertThat(m).containsEntry("enabled", false));
    }

    @Test
    @DisplayName("weekly goals register with the tick's own cron (Sunday 06:00 UTC by default), never a second schedule")
    void weeklyGoalsUseTheTicksCron() {
        PlatformJobs fresh = new PlatformJobs(new org.springframework.boot.autoconfigure.jdbc.DataSourceProperties(), jdbc,
                new com.fasterxml.jackson.databind.ObjectMapper(), "");
        new DadCoachPlatformJobs(fresh, weeklyGoalJob, sessionTimersOwner, personDeletions, "0 0 6 * * SUN");
        assertThat(fresh.job("weekly_goals")).hasValueSatisfying(j -> {
            assertThat(j.cron()).isEqualTo("0 0 6 * * SUN");
            assertThat(j.zone()).isEqualTo("UTC");
            assertThat(j.every()).isNull();
        });
    }

    @Test
    @DisplayName("J-5 watchdog for a cron job: silent only when its last expected fire is over an hour old and nothing ran since - a restart is no floor")
    void cronWatchdog() {
        PlatformJobs.Job weekly = PlatformJobs.Job.cron("t_cron", "0 0 6 * * SUN", "UTC", Duration.ofMinutes(5), false, "test", () -> true);
        Instant sundayNoon = Instant.parse("2026-11-08T12:00:00Z");
        assertThat(PlatformJobs.lastExpectedFire(weekly, sundayNoon)).isEqualTo(Instant.parse("2026-11-08T06:00:00Z"));
        assertThat(PlatformJobs.silent(weekly, Instant.parse("2026-11-08T06:00:05Z"), sundayNoon)).as("ran at 06:00").isFalse();
        assertThat(PlatformJobs.silent(weekly, Instant.parse("2026-11-01T06:00:05Z"), sundayNoon)).as("last ran a week ago").isTrue();
        assertThat(PlatformJobs.silent(weekly, Instant.EPOCH, Instant.parse("2026-11-08T06:30:00Z"))).as("within the hour's grace").isFalse();
    }

    @Test
    @DisplayName("the tick runs the tick's own body (not the platform's); an unregistered job still ticks, unlocked")
    void tickRunsItsOwnBody() {
        counting("t_body", false);
        AtomicInteger here = new AtomicInteger();
        jobs.tick("t_body", here::incrementAndGet);
        assertThat(here.get()).isEqualTo(1);
        assertThat(counter.get()).as("the platform body is not the tick's").isZero();
        jobs.tick("t_never_registered", here::incrementAndGet);
        assertThat(here.get()).as("a missing registration never stops a job").isEqualTo(2);
    }

    @Test
    @DisplayName("J-7: 8 concurrent calls of one slot on real threads run the job once; the rest answer BUSY or DUPLICATE")
    void concurrentCallsOfOneSlot() throws Exception {
        jobs.register(PlatformJobs.Job.every("t_race", Duration.ofMinutes(5), Duration.ofMinutes(1), false, "test", () -> {
            Thread.sleep(300);
            return Map.of("n", counter.incrementAndGet());
        }));
        jobs.setPlatformOwned(Set.of("t_race"));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<PlatformJobs.Outcome>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(pool.submit(() -> {
                go.await();
                return jobs.runForPlatform("t_race", slot(21));
            }));
        }
        go.countDown();
        List<Object> statuses = new ArrayList<>();
        for (Future<PlatformJobs.Outcome> f : calls) {
            statuses.add(f.get(30, TimeUnit.SECONDS).body().get("status"));
        }
        pool.shutdown();
        assertThat(counter.get()).isEqualTo(1);
        assertThat(statuses).filteredOn("DONE"::equals).hasSize(1);
        assertThat(statuses).allMatch(st -> Set.of("DONE", "BUSY", "DUPLICATE").contains(st));
        assertThat(jobs.runForPlatform("t_race", slot(21)).body()).containsEntry("status", "DUPLICATE");
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("J-7: a tick running when the owner flips to the platform - the platform's call answers BUSY until it ends, then runs the slot once")
    void tickAndPlatformCallNeverOverlap() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        Runnable body = () -> {
            maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
            try {
                inside.countDown();
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                concurrent.decrementAndGet();
            }
        };
        jobs.register(PlatformJobs.Job.every("t_flip", Duration.ofMinutes(5), Duration.ofMinutes(1), false, "test", () -> {
            body.run();
            return true;
        }));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> tick = pool.submit(() -> jobs.tick("t_flip", body));
        assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();
        jobs.setPlatformOwned(Set.of("t_flip"));
        assertThat(jobs.runForPlatform("t_flip", slot(22)).httpStatus()).isEqualTo(409);
        release.countDown();
        tick.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(jobs.runForPlatform("t_flip", slot(22)).body()).containsEntry("status", "DONE");
        assertThat(maxConcurrent.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("J-5 watchdog: a PLATFORM job not run for 3x its interval runs here once; a recent ledger start (any instance) or a running job is no alarm")
    void watchdog() throws Exception {
        jobs.register(PlatformJobs.Job.every("t_wd", Duration.ofMillis(200), Duration.ofMinutes(1), false, "test",
                () -> Map.of("n", counter.incrementAndGet())));
        jobs.setPlatformOwned(Set.of("t_wd"));
        jdbc.update("INSERT INTO platform_job_run (job_key, slot, status, started_at, finished_at) VALUES ('t_wd', ?, 'DONE', now() + interval '1 hour', now())",
                Timestamp.from(slot(23)));
        Thread.sleep(700);
        jobs.watchdog();
        assertThat(counter.get()).as("another instance ran it recently (the ledger)").isZero();
        jdbc.update("DELETE FROM platform_job_run WHERE job_key = 't_wd'");
        try (Connection other = dataSource.getConnection()) {
            lock(other, PlatformJobs.LOCK_PREFIX + "t_wd");
            jobs.watchdog();
            assertThat(counter.get()).as("still running elsewhere (its lock held)").isZero();
            unlock(other, PlatformJobs.LOCK_PREFIX + "t_wd");
        }
        jobs.watchdog();
        assertThat(counter.get()).isEqualTo(1);
        jobs.watchdog();
        assertThat(counter.get()).as("once per silence, not every minute").isEqualTo(1);
    }

    @Test
    @DisplayName("the startup registration PUTs the full list to /api/v1/worker/jobs with the worker's key; a 404 (no job API) is not retried")
    void syncOverHttp() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        List<String> keys = new CopyOnWriteArrayList<>();
        AtomicInteger answer = new AtomicInteger(200);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/worker/jobs", exchange -> {
            bodies.add(exchange.getRequestMethod() + " " + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-API-Key")));
            exchange.sendResponseHeaders(answer.get(), -1);
            exchange.close();
        });
        server.start();
        try {
            com.dadcoach.integration.platform.WorkflowPlatformProperties properties = new com.dadcoach.integration.platform.WorkflowPlatformProperties();
            properties.setEnabled(true);
            properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
            properties.setApiKey("test-worker-key");
            PlatformJobsSync http = new PlatformJobsSync(jobs, properties, new com.fasterxml.jackson.databind.ObjectMapper(), "dad_3", true);
            assertThat(http.sync()).isEqualTo(200);
            assertThat(bodies).singleElement().satisfies(b -> assertThat(b).startsWith("PUT ").contains("\"workerKey\":\"dad_3\"")
                    .contains("\"jobKey\":\"qa_probe\""));
            assertThat(keys).containsExactly("test-worker-key");
            answer.set(404);
            http.syncWithRetries();
            assertThat(bodies).hasSize(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("production starts lazily (Dockerfile): the registration and the jobs bean are eager, or no job is ever registered")
    void eagerUnderLazyInitialization() {
        for (Class<?> c : List.of(DadCoachPlatformJobs.class, PlatformJobs.class)) {
            org.springframework.context.annotation.Lazy lazy = c.getAnnotation(org.springframework.context.annotation.Lazy.class);
            assertThat(lazy).as(c.getSimpleName()).isNotNull();
            assertThat(lazy.value()).as(c.getSimpleName()).isFalse();
        }
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
