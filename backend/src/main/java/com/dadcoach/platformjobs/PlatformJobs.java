package com.dadcoach.platformjobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
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
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Unified Workflow 5.4 v3 (ai-workflow-platform PHASE5_4_SPEC.md §v3.3, §v3.8): Dad Coach's scheduled jobs, run either by
 * their own {@code @Scheduled} tick (PRODUCT, today) or by the platform's product-job scheduler (PLATFORM: the platform
 * calls {@code POST /api/integration/jobs/{jobKey}} once per slot). {@code DC_PLATFORM_JOBS} lists the jobs the platform
 * owns (empty = all PRODUCT); a PLATFORM job's tick does nothing, a PRODUCT job's platform call answers PRODUCT_OWNED - a
 * job never runs on both sides. A {@code platformOnly} job (D-055: session timers, person deletions) has no tick and is
 * always the platform's.
 *
 * <p>Every run, whichever side starts it, holds the job's Postgres advisory lock (and, for a job that sends WhatsApp, the
 * messaging lane's - D-063's "one sending job at a time", now across instances too) on a connection of its own for the
 * whole run. Postgres frees a lock when its process dies, so liveness is the lock, not an age: a busy job answers BUSY,
 * a slot left RUNNING by a dead process is taken over.</p>
 *
 * <p>The lock connections come from a small pool of their own (review: holding a lock must never take a connection the
 * job's own work - or a user's request - needs from the main pool). A platform call waits only briefly for the messaging
 * lane and otherwise answers BUSY (the platform calls again in 30 s, not counted as an attempt).</p>
 */
// eager even with spring.main.lazy-initialization=true (the Dockerfile sets it): a lazy one never registers its jobs
@org.springframework.context.annotation.Lazy(false)
@Component
public class PlatformJobs {

    private static final Logger log = LoggerFactory.getLogger(PlatformJobs.class);
    static final String LOCK_PREFIX = "dadcoach:job:";
    static final String LANE_LOCK = "dadcoach:lane:messaging";
    /** How long a tick waits for the messaging lane (the in-process lanes' old queueing). */
    static final Duration LANE_WAIT = Duration.ofSeconds(30);
    /** How long a platform call waits for it before answering BUSY. */
    static final Duration PLATFORM_LANE_WAIT = Duration.ofSeconds(5);
    private volatile Duration laneWait = LANE_WAIT;
    private volatile Duration platformLaneWait = PLATFORM_LANE_WAIT;

    /** Tests shorten the lane waits. */
    void setLaneWait(Duration laneWait) {
        this.laneWait = laneWait;
        this.platformLaneWait = laneWait.compareTo(PLATFORM_LANE_WAIT) < 0 ? laneWait : PLATFORM_LANE_WAIT;
    }

    /** A job: its key, schedule (every or cron + zone), the platform's timeout for it, whether it sends, and its body. */
    public record Job(String key, Duration every, String cron, String zone, Duration timeout, boolean sends, String description,
                      Callable<Object> body, boolean platformOnly) {

        public static Job every(String key, Duration every, Duration timeout, boolean sends, String description, Callable<Object> body) {
            return new Job(key, every, null, null, timeout, sends, description, body, false);
        }

        public static Job cron(String key, String cron, String zone, Duration timeout, boolean sends, String description,
                               Callable<Object> body) {
            return new Job(key, null, cron, zone, timeout, sends, description, body, false);
        }

        /**
         * A job with no {@code @Scheduled} tick left (D-055): the platform always owns it, whatever {@code DC_PLATFORM_JOBS}
         * says - a list that forgot it can never leave it without a scheduler.
         */
        public Job asPlatformOnly() {
            return new Job(key, every, cron, zone, timeout, sends, description, body, true);
        }
    }

    /** What a platform call came to. */
    public record Outcome(int httpStatus, Map<String, Object> body) {}

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastPlatformCall = new ConcurrentHashMap<>();
    private final Set<String> silentReported = ConcurrentHashMap.newKeySet();
    private final DataSourceProperties dataSourceProperties;
    private volatile HikariDataSource lockPool;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private volatile Set<String> platformOwned;
    private final Instant started = Instant.now();
    private int lockPoolSize = 4;

    /** The lock pool's size (default 4; the tests' many cached contexts use fewer). */
    @org.springframework.beans.factory.annotation.Autowired
    void setLockPoolSize(@Value("${app.platform-jobs.lock-pool-size:4}") int lockPoolSize) {
        this.lockPoolSize = lockPoolSize;
    }

    public PlatformJobs(DataSourceProperties dataSourceProperties, JdbcTemplate jdbc, ObjectMapper json,
                        @Value("${app.platform-jobs.platform-owned:}") String platformOwned) {
        this.dataSourceProperties = dataSourceProperties;
        this.jdbc = jdbc;
        this.json = json;
        this.platformOwned = parse(platformOwned);
    }

    /** The lock connections' own pool (lazy: built on the first run; idle connections close after 10 s). */
    private HikariDataSource lockPool() {
        HikariDataSource pool = lockPool;
        if (pool == null) {
            synchronized (this) {
                if (lockPool == null) {
                    HikariDataSource p = dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
                    p.setPoolName("platform-job-locks");
                    p.setMaximumPoolSize(lockPoolSize);
                    p.setMinimumIdle(0);
                    p.setIdleTimeout(Duration.ofSeconds(10).toMillis());
                    p.setConnectionTimeout(Duration.ofSeconds(5).toMillis());
                    lockPool = p;
                }
                pool = lockPool;
            }
        }
        return pool;
    }

    @PreDestroy
    void closeLockPool() {
        HikariDataSource pool = lockPool;
        if (pool != null) {
            pool.close();
        }
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

    /** Tests remove the jobs they registered (the shared context's registration body stays the real one). */
    void unregister(String key) {
        jobs.remove(key);
    }

    public Collection<Job> jobs() {
        return List.copyOf(jobs.values());
    }

    public Optional<Job> job(String key) {
        return Optional.ofNullable(jobs.get(key));
    }

    public boolean platformOwns(String key) {
        Job job = jobs.get(key);
        return (job != null && job.platformOnly()) || platformOwned.contains(key);
    }

    /** Tests flip owners on the one cached context. */
    void setPlatformOwned(Set<String> keys) {
        this.platformOwned = Set.copyOf(keys);
    }

    // --------------------------------------------------------------------------------------------- the product's tick

    /**
     * The job's {@code @Scheduled} tick: nothing when the platform owns it; otherwise {@code here} - the tick's own body,
     * exactly as before (its guard, its summary log, its catch) - under the job's locks. A run already holding them
     * (another instance during a deploy, the platform's call) means this tick is skipped (logged). A job that is not
     * registered runs {@code here} as before, unlocked - a missing registration never stops a job.
     */
    public void tick(String key, Runnable here) {
        TickResult r = tickWith(key, here);
        if (r == TickResult.FAILED) {
            log.atWarn().addKeyValue("jobKey", key).log("platform_jobs.tick_not_run");
        }
    }

    /** What a tick came to. */
    public enum TickResult { RAN, PLATFORM_OWNED, SKIPPED, FAILED }

    /**
     * The tick under the job's locks: PLATFORM_OWNED (nothing ran), SKIPPED (the job is running elsewhere - logged),
     * FAILED (the locks could not be taken: a database error - logged), RAN (the tick's own failure is logged ERROR).
     */
    public TickResult tickWith(String key, Runnable tick) {
        Job job = jobs.get(key);
        if (job == null) {
            tick.run();
            return TickResult.RAN;
        }
        if (platformOwns(key)) {
            return TickResult.PLATFORM_OWNED;
        }
        RuntimeException[] bodyFailure = new RuntimeException[1];
        boolean ran;
        try {
            ran = runLocked(job, laneWait, () -> {
                try {
                    tick.run();
                } catch (RuntimeException e) {
                    bodyFailure[0] = e;
                }
                return true;
            }).isPresent();
        } catch (Exception e) {
            log.atWarn().addKeyValue("jobKey", key).setCause(e).log("platform_jobs.tick_failed");
            return TickResult.FAILED;
        }
        if (bodyFailure[0] != null) {
            // the tick's own failure, as an uncaught @Scheduled failure was logged before (ERROR)
            log.atError().addKeyValue("jobKey", key).setCause(bodyFailure[0]).log("platform_jobs.tick_body_failed");
            return TickResult.RAN;
        }
        if (!ran) {
            log.atInfo().addKeyValue("jobKey", key).log("platform_jobs.tick_skipped");
            return TickResult.SKIPPED;
        }
        return TickResult.RAN;
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
            Optional<Outcome> outcome = runLocked(job, platformLaneWait, () -> runSlot(job, slot));
            return outcome.orElseGet(() -> new Outcome(409, Map.of("status", "BUSY")));
        } catch (SQLTransientConnectionException e) {
            if (e.getCause() == null) {
                // the lock pool is exhausted (other runs hold it): busy, asked again shortly
                log.atInfo().addKeyValue("jobKey", key).log("platform_jobs.lock_pool_busy");
                return new Outcome(409, Map.of("status", "BUSY"));
            }
            log.atWarn().addKeyValue("jobKey", key).addKeyValue("slot", slot).setCause(e).log("platform_jobs.run_failed");
            return new Outcome(500, Map.of("status", "FAILED", "error", "DATABASE_UNREACHABLE"));
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
     * for up to {@code laneWait}), both on one connection of the lock pool, released in {@code finally} (a connection
     * whose unlock failed is evicted, never handed out still holding a lock). Empty when a lock could not be had.
     */
    <T> Optional<T> runLocked(Job job, Duration laneWait, Callable<T> work) throws Exception {
        HikariDataSource pool = lockPool();
        Connection c = pool.getConnection();
        boolean healthy = true;
        try {
            c.setAutoCommit(true);
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
                        healthy &= unlock(c, LANE_LOCK);
                    }
                }
            } finally {
                healthy &= unlock(c, LOCK_PREFIX + job.key());
            }
        } finally {
            if (healthy) {
                c.close();
            } else {
                pool.evictConnection(c);
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

    private static boolean unlock(Connection c, String name) {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 0))")) {
            ps.setString(1, name);
            ps.executeQuery().close();
            return true;
        } catch (SQLException e) {
            log.atWarn().addKeyValue("lock", name).log("platform_jobs.unlock_failed");
            return false;
        }
    }

    // --------------------------------------------------------------------------------------------- watchdog + purge

    /**
     * Review J-5: a PLATFORM job the platform has not run for 3× its interval is run once here, under its locks - a
     * platform that stopped calling never silently stops a job. "Has not run": an interval job - no slot started in the
     * shared ledger (any instance) and no call to this instance; a cron job (D-062) - no call to this instance since its
     * last expected fire and no DONE row for that slot. A job still running (its lock held) is no alarm either. ERROR the
     * first time per job, WARN after.
     */
    private volatile Instant lastPurge = Instant.EPOCH;

    /** Every minute (one job per class in Dad Coach's lane table): the watchdog, and once a day the ledger purge. */
    @org.springframework.scheduling.annotation.Scheduled(scheduler = com.dadcoach.config.SchedulingLanes.HOUSEKEEPING, initialDelayString = "${app.platform-jobs.watchdog-initial-delay:PT2M}", fixedDelayString = "PT1M")
    public void maintenance() {
        watchdog();
        if (Duration.between(lastPurge, Instant.now()).compareTo(Duration.ofDays(1)) > 0) {
            lastPurge = Instant.now();
            purge();
        }
    }

    public void watchdog() {
        watchdog(jobs.values());
    }

    /** Tests: a restart - what this process remembers of the platform's calls is gone, the ledger stays. */
    void forgetPlatformCallsForTests() {
        lastPlatformCall.clear();
    }

    /** The watchdog over {@code these} jobs (tests pass their own, never running the real ones). */
    void watchdog(Collection<Job> these) {
        Instant now = Instant.now();
        for (Job job : these) {
            if (!platformOwns(job.key())) {
                continue;
            }
            // D-062: a cron job is covered by its slot's DONE row (the platform's run or the watchdog's), never by a
            // start time - a run cut off mid-slot leaves a RUNNING row that the run below takes over
            Instant last = job.cron() != null ? lastPlatformCall.getOrDefault(job.key(), Instant.EPOCH)
                    : latest(lastPlatformCall.getOrDefault(job.key(), started), lastLedgerStart(job.key()));
            if (!silent(job, last, now)) {
                continue;
            }
            if (job.cron() != null && doneResult(job.key(), lastExpectedFire(job, now)).isPresent()) {
                lastPlatformCall.put(job.key(), now);
                continue;
            }
            try {
                Optional<Boolean> ran = runLocked(job, laneWait, () -> {
                    lastPlatformCall.put(job.key(), Instant.now());
                    var entry = silentReported.add(job.key()) ? log.atError() : log.atWarn();
                    entry.addKeyValue("jobKey", job.key()).addKeyValue("silentFor", Duration.between(last, now))
                            .log("platform_jobs.platform_silent - running the job here once");
                    if (job.cron() != null) {
                        // the missed slot's own ledger row (as the platform's run would write it): a restart finds it,
                        // and a late platform call for the slot is a duplicate - never a second run
                        runSlot(job, lastExpectedFire(job, now));
                    } else {
                        job.body().call();
                    }
                    return true;
                });
                if (ran.isEmpty()) {
                    log.atInfo().addKeyValue("jobKey", job.key()).log("platform_jobs.watchdog_skipped_running");
                }
            } catch (Exception e) {
                log.atWarn().addKeyValue("jobKey", job.key()).setCause(e).log("platform_jobs.watchdog_run_failed");
            }
        }
    }

    /**
     * An interval job: no run for 3× its interval. A cron job: its last expected fire is more than an hour ago and
     * {@code last} is before it (the process start is no floor - a restart must not hide a platform that stopped calling;
     * the watchdog then checks the slot's DONE row).
     */
    static boolean silent(Job job, Instant last, Instant now) {
        if (job.every() != null) {
            return Duration.between(last, now).compareTo(job.every().multipliedBy(3)) > 0;
        }
        Instant expected = lastExpectedFire(job, now);
        return expected != null && expected.plus(Duration.ofHours(1)).isBefore(now) && last.isBefore(expected);
    }

    /** The cron job's last fire at or before {@code now} (searched over the last 8 days; null when none). */
    static Instant lastExpectedFire(Job job, Instant now) {
        org.springframework.scheduling.support.CronExpression cron = org.springframework.scheduling.support.CronExpression.parse(job.cron());
        java.time.ZoneId zone = java.time.ZoneId.of(job.zone() == null ? "UTC" : job.zone());
        java.time.ZonedDateTime t = now.minus(Duration.ofDays(8)).atZone(zone);
        Instant found = null;
        while (true) {
            java.time.ZonedDateTime next = cron.next(t);
            if (next == null || next.toInstant().isAfter(now)) {
                return found;
            }
            found = next.toInstant();
            t = next;
        }
    }

    private Instant lastLedgerStart(String key) {
        try {
            Timestamp t = jdbc.queryForObject("SELECT max(started_at) FROM platform_job_run WHERE job_key = ?", Timestamp.class, key);
            return t == null ? Instant.EPOCH : t.toInstant();
        } catch (RuntimeException e) {
            return Instant.EPOCH;
        }
    }

    private static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    public void purge() {
        try {
            jdbc.update("DELETE FROM platform_job_run WHERE started_at < now() - interval '14 days'");
        } catch (RuntimeException e) {
            log.atWarn().log("platform_jobs.purge_failed");
        }
    }
}
