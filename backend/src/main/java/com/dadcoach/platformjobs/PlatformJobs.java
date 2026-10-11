package com.dadcoach.platformjobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Unified Workflow 5.4 v3 (ai-workflow-platform PHASE5_4_SPEC.md §v3.3, §v3.8): Dad Coach's scheduled jobs, run either by
 * their own {@code @Scheduled} tick (PRODUCT, today) or by the platform's product-job scheduler (PLATFORM: the platform
 * calls {@code POST /api/integration/jobs/{jobKey}} once per slot). {@code DC_PLATFORM_JOBS} lists the jobs the platform
 * owns (empty = all PRODUCT); a PLATFORM job's tick does nothing, a PRODUCT job's platform call answers PRODUCT_OWNED - a
 * job never runs on both sides.
 *
 * <p>Every run, whichever side starts it, holds the job's Postgres advisory lock (and, for a job that sends WhatsApp, the
 * messaging lane's - D-063's "one sending job at a time", now across instances too) on a connection of its own for the
 * whole run. Postgres frees a lock when its process dies, so liveness is the lock, not an age: a busy job answers BUSY,
 * a slot left RUNNING by a dead process is taken over.</p>
 */
@Component
public class PlatformJobs {

    private static final Logger log = LoggerFactory.getLogger(PlatformJobs.class);
    static final String LOCK_PREFIX = "dadcoach:job:";
    static final String LANE_LOCK = "dadcoach:lane:messaging";
    static final Duration LANE_WAIT = Duration.ofSeconds(30);
    private volatile Duration laneWait = LANE_WAIT;

    /** Tests shorten the lane wait. */
    void setLaneWait(Duration laneWait) {
        this.laneWait = laneWait;
    }

    /** A job: its key, schedule (every or cron + zone), the platform's timeout for it, whether it sends, and its body. */
    public record Job(String key, Duration every, String cron, String zone, Duration timeout, boolean sends, String description,
                      Callable<Object> body) {

        public static Job every(String key, Duration every, Duration timeout, boolean sends, String description, Callable<Object> body) {
            return new Job(key, every, null, null, timeout, sends, description, body);
        }

        public static Job cron(String key, String cron, String zone, Duration timeout, boolean sends, String description,
                               Callable<Object> body) {
            return new Job(key, null, cron, zone, timeout, sends, description, body);
        }
    }

    /** What a platform call came to. */
    public record Outcome(int httpStatus, Map<String, Object> body) {}

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastPlatformCall = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private volatile Set<String> platformOwned;
    private final Instant started = Instant.now();

    public PlatformJobs(DataSource dataSource, JdbcTemplate jdbc, ObjectMapper json,
                        @Value("${app.platform-jobs.platform-owned:}") String platformOwned) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.json = json;
        this.platformOwned = parse(platformOwned);
    }

    static Set<String> parse(String list) {
        return list == null ? Set.of() : Arrays.stream(list.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private volatile QaProbe probe;

    void registerProbe(QaProbe probe) {
        this.probe = probe;
    }

    public Optional<QaProbe> probe() {
        return Optional.ofNullable(probe);
    }

    public void register(Job job) {
        jobs.put(job.key(), job);
    }

    public Collection<Job> jobs() {
        return List.copyOf(jobs.values());
    }

    public Optional<Job> job(String key) {
        return Optional.ofNullable(jobs.get(key));
    }

    public boolean platformOwns(String key) {
        return platformOwned.contains(key);
    }

    /** Tests flip owners on the one cached context. */
    void setPlatformOwned(Set<String> keys) {
        this.platformOwned = Set.copyOf(keys);
    }

    // --------------------------------------------------------------------------------------------- the product's tick

    /**
     * The job's {@code @Scheduled} tick: nothing when the platform owns it; otherwise its body under its locks (a run
     * already holding them - another instance during a deploy, the platform's call - means this tick is skipped). Never
     * throws (the tick's old behaviour: a failure is logged and the next tick tries again).
     */
    public void tick(String key) {
        Job job = jobs.get(key);
        if (job == null || platformOwns(key)) {
            return;
        }
        try {
            runLocked(job, () -> {
                job.body().call();
                return true;
            });
        } catch (Exception e) {
            log.atWarn().addKeyValue("jobKey", key).setCause(e).log("platform_jobs.tick_failed");
        }
    }

    /**
     * Dad Coach: a job's own tick (JobRuns) when its body is the tick itself - nothing when the platform owns the job; else the
     * tick under the job's locks (skipped when they are taken). Returns false when it did not run.
     */
    public boolean tickWith(String key, Runnable tick) {
        Job job = jobs.get(key);
        if (job == null) {
            tick.run();
            return true;
        }
        if (platformOwns(key)) {
            return false;
        }
        try {
            return runLocked(job, () -> {
                tick.run();
                return true;
            }).isPresent();
        } catch (Exception e) {
            log.atWarn().addKeyValue("jobKey", key).setCause(e).log("platform_jobs.tick_failed");
            return false;
        }
    }

    // ------------------------------------------------------------------------- a platform run, seen by JobRuns (Dad Coach)

    private static final ThreadLocal<String> PLATFORM_RUN = new ThreadLocal<>();
    private static final ThreadLocal<Object> PLATFORM_RESULT = new ThreadLocal<>();

    /** Whether this thread is running {@code key} for the platform (JobRuns then runs it as is and rethrows). */
    public static boolean platformRun(String key) {
        return key.equals(PLATFORM_RUN.get());
    }

    public static void platformResult(Object result) {
        PLATFORM_RESULT.set(result);
    }

    /** Runs {@code tick} as the platform's run of {@code key}; returns what JobRuns recorded for it. */
    public static Object asPlatformRun(String key, Runnable tick) {
        PLATFORM_RUN.set(key);
        PLATFORM_RESULT.remove();
        try {
            tick.run();
            Object result = PLATFORM_RESULT.get();
            return result == null ? "DONE" : result;
        } finally {
            PLATFORM_RUN.remove();
            PLATFORM_RESULT.remove();
        }
    }

    // --------------------------------------------------------------------------------------------- the platform's call

    /** One platform call for {@code slot}: PRODUCT_OWNED, DUPLICATE (the slot's first result), BUSY (409), DONE, or 500. */
    public Outcome runForPlatform(String key, Instant slot) {
        Job job = jobs.get(key);
        if (job == null) {
            return new Outcome(404, Map.of("status", "UNKNOWN_JOB"));
        }
        lastPlatformCall.put(key, Instant.now());
        if (!platformOwns(key)) {
            return new Outcome(200, Map.of("status", "PRODUCT_OWNED"));
        }
        Optional<String> done = doneResult(key, slot);
        if (done.isPresent()) {
            return new Outcome(200, duplicate(done.get()));
        }
        try {
            Optional<Outcome> outcome = runLocked(job, () -> runSlot(job, slot));
            return outcome.orElseGet(() -> new Outcome(409, Map.of("status", "BUSY")));
        } catch (Exception e) {
            log.atWarn().addKeyValue("jobKey", key).addKeyValue("slot", slot).setCause(e).log("platform_jobs.run_failed");
            return new Outcome(500, Map.of("status", "FAILED", "error", e.getClass().getSimpleName()));
        }
    }

    /** Under the job's locks: the ledger row (a DONE row answers; a RUNNING one is a dead holder's and is taken over). */
    private Outcome runSlot(Job job, Instant slot) throws Exception {
        Optional<String> done = doneResult(job.key(), slot);
        if (done.isPresent()) {
            return new Outcome(200, duplicate(done.get()));
        }
        int took = jdbc.update("INSERT INTO platform_job_run (job_key, slot, status, started_at) VALUES (?, ?, 'RUNNING', now()) "
                + "ON CONFLICT (job_key, slot) DO UPDATE SET status = 'RUNNING', started_at = now() WHERE platform_job_run.status = 'RUNNING'",
                job.key(), Timestamp.from(slot));
        if (took != 1) {
            return doneResult(job.key(), slot).map(r -> new Outcome(200, duplicate(r)))
                    .orElse(new Outcome(409, Map.of("status", "BUSY")));
        }
        Object result;
        try {
            result = job.body().call();
        } catch (Exception e) {
            jdbc.update("DELETE FROM platform_job_run WHERE job_key = ? AND slot = ? AND status = 'RUNNING'", job.key(), Timestamp.from(slot));
            throw e;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "DONE");
        body.put("jobKey", job.key());
        body.put("slot", slot.toString());
        if (result != null && !(result instanceof Boolean)) {
            body.put("result", result);
        }
        jdbc.update("UPDATE platform_job_run SET status = 'DONE', result = ?::jsonb, finished_at = now() WHERE job_key = ? AND slot = ?",
                json.writeValueAsString(body), job.key(), Timestamp.from(slot));
        log.atInfo().addKeyValue("jobKey", job.key()).addKeyValue("slot", slot).log("platform_jobs.done");
        return new Outcome(200, body);
    }

    private Optional<String> doneResult(String key, Instant slot) {
        return jdbc.queryForList("SELECT result::text FROM platform_job_run WHERE job_key = ? AND slot = ? AND status = 'DONE'",
                String.class, key, Timestamp.from(slot)).stream().findFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> duplicate(String stored) {
        try {
            Map<String, Object> first = stored == null ? Map.of() : json.readValue(stored, Map.class);
            Map<String, Object> out = new LinkedHashMap<>(first);
            out.put("status", "DUPLICATE");
            return out;
        } catch (Exception e) {
            return Map.of("status", "DUPLICATE");
        }
    }

    // --------------------------------------------------------------------------------------------- the locks

    /**
     * Runs {@code work} holding the job's advisory lock (tried once) and, for a sending job, the messaging lane's (waited
     * for up to {@link #LANE_WAIT}), both on one dedicated connection, released in {@code finally}. Empty when a lock
     * could not be had.
     */
    <T> Optional<T> runLocked(Job job, Callable<T> work) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(true);
            try {
                if (!tryLock(c, LOCK_PREFIX + job.key())) {
                    return Optional.empty();
                }
                try {
                    if (job.sends() && !waitLock(c, LANE_LOCK, laneWait)) {
                        return Optional.empty();
                    }
                    try {
                        return Optional.ofNullable(work.call());
                    } finally {
                        if (job.sends()) {
                            unlock(c, LANE_LOCK);
                        }
                    }
                } finally {
                    unlock(c, LOCK_PREFIX + job.key());
                }
            } finally {
                c.setAutoCommit(autoCommit);
            }
        }
    }

    private static boolean tryLock(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?, 0))")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static boolean waitLock(Connection c, String name, Duration wait) throws SQLException, InterruptedException {
        long until = System.nanoTime() + wait.toNanos();
        while (true) {
            if (tryLock(c, name)) {
                return true;
            }
            if (System.nanoTime() > until) {
                return false;
            }
            Thread.sleep(200);
        }
    }

    private static void unlock(Connection c, String name) {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 0))")) {
            ps.setString(1, name);
            ps.executeQuery().close();
        } catch (SQLException e) {
            log.atWarn().addKeyValue("lock", name).log("platform_jobs.unlock_failed");
        }
    }

    // --------------------------------------------------------------------------------------------- watchdog + purge

    /**
     * Review J-5: a PLATFORM job the platform has not called for 3× its interval (and not since this process started) is
     * run once here, under its locks, and logged ERROR - a platform that stopped calling never silently stops a job.
     */
    private volatile Instant lastPurge = Instant.EPOCH;

    /** Every minute (one job per class in Dad Coach's lane table): the watchdog, and once a day the ledger purge. */
    @org.springframework.scheduling.annotation.Scheduled(scheduler = com.dadcoach.config.SchedulingLanes.HOUSEKEEPING, initialDelayString = "PT2M", fixedDelayString = "PT1M")
    public void maintenance() {
        watchdog();
        if (Duration.between(lastPurge, Instant.now()).compareTo(Duration.ofDays(1)) > 0) {
            lastPurge = Instant.now();
            purge();
        }
    }

    public void watchdog() {
        Instant now = Instant.now();
        for (Job job : jobs.values()) {
            if (!platformOwns(job.key())) {
                continue;
            }
            Duration interval = job.every() != null ? job.every() : Duration.ofDays(7);
            Instant last = lastPlatformCall.getOrDefault(job.key(), started);
            if (Duration.between(last, now).compareTo(interval.multipliedBy(3)) > 0) {
                lastPlatformCall.put(job.key(), now);
                log.atError().addKeyValue("jobKey", job.key()).addKeyValue("silentFor", Duration.between(last, now))
                        .log("platform_jobs.platform_silent - running the job here once");
                try {
                    runLocked(job, () -> {
                        job.body().call();
                        return true;
                    });
                } catch (Exception e) {
                    log.atWarn().addKeyValue("jobKey", job.key()).setCause(e).log("platform_jobs.watchdog_run_failed");
                }
            }
        }
    }

    public void purge() {
        try {
            jdbc.update("DELETE FROM platform_job_run WHERE started_at < now() - interval '14 days'");
        } catch (RuntimeException e) {
            log.atWarn().log("platform_jobs.purge_failed");
        }
    }
}
