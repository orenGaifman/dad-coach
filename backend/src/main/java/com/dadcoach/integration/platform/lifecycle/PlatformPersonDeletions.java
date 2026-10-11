package com.dadcoach.integration.platform.lifecycle;

import com.dadcoach.config.SchedulingLanes;
import com.dadcoach.domain.father.FatherDataPurger;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * A father deleted for good is deleted on the AI Workflow Platform too - through its generic person lifecycle
 * API; the platform owns that deletion (his person, conversations, messages, executions, scheduled turns).
 *
 * <p>Outbox: {@link #request} writes a row in the caller's transaction (the deletion itself), so the request
 * exists exactly when the father is deleted. After commit it is sent at once in the background; a failure is
 * retried with backoff (1, 2, 4 ... minutes, at most every 6 hours) by a scan every few minutes, until the
 * platform confirms - never dropped. Each attempt: (1) register the person under his ref and INACTIVE (persons
 * from before refs were sent had none; INACTIVE stops his scheduled turns at once), (2) delete it. Both are
 * idempotent. Then, for a self-service deletion, his own Dad Coach data is purged. From the
 * {@value #OVERDUE_ATTEMPTS}th failed attempt every retry is logged as an error.</p>
 */
@Service
public class PlatformPersonDeletions {

    private static final Logger log = LoggerFactory.getLogger(PlatformPersonDeletions.class);
    static final int OVERDUE_ATTEMPTS = 6;
    private static final Duration MAX_BACKOFF = Duration.ofHours(6);
    private static final int BATCH = 50;
    private static final Pattern CODE = Pattern.compile("\"(?:code|error_code)\"\\s*:\\s*\"([^\"]{1,80})\"");

    private final JdbcTemplate jdbc;
    private final PlatformTenancyClient platform;
    private final FatherDataPurger purger;
    private final boolean enabled;
    private final Clock clock;
    private final Consumer<Runnable> executor;
    private final ExecutorService ownedExecutor;

    @Autowired
    public PlatformPersonDeletions(JdbcTemplate jdbc, PlatformTenancyClient platform, FatherDataPurger purger,
                                   WorkflowPlatformProperties config,
                                   @org.springframework.beans.factory.annotation.Value("${dadcoach.platform-person-deletion.send-after-commit:true}")
                                   boolean sendAfterCommit) {
        this.jdbc = jdbc;
        this.platform = platform;
        this.purger = purger;
        this.enabled = config.isEnabled();
        this.clock = Clock.systemUTC();
        this.ownedExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "platform-person-deletion");
            thread.setDaemon(true);
            return thread;
        });
        // tests switch the immediate send off and drive sendDue() themselves (no background race)
        this.executor = sendAfterCommit ? ownedExecutor::execute : task -> { };
    }

    /** For tests: switched on or off, a given clock, and the after-commit send on the given executor. */
    PlatformPersonDeletions(JdbcTemplate jdbc, PlatformTenancyClient platform, FatherDataPurger purger, boolean enabled,
                            Clock clock, Consumer<Runnable> executor) {
        this.jdbc = jdbc;
        this.platform = platform;
        this.purger = purger;
        this.enabled = enabled;
        this.clock = clock;
        this.executor = executor;
        this.ownedExecutor = null;
    }

    @PreDestroy
    void shutdown() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
        }
    }

    /**
     * Records that the platform must delete this father's person - in the caller's transaction - and sends it after
     * commit. {@code purgeLocal}: purge his Dad Coach data once the platform confirms (self-service deletion).
     * A second request for the same father changes nothing.
     */
    public void request(Long fatherId, String phone, boolean purgeLocal) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO platform_person_deletion (father_id, person_ref, external_user_id, purge_local, requested_at, next_attempt_at)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (father_id) DO NOTHING""",
                fatherId, PersonRefs.of(fatherId), phone == null ? null : PersonRefs.whatsappId(phone), purgeLocal, now, now);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendSoon();
                }
            });
        } else {
            sendSoon();
        }
    }

    /** True while a deletion of this WhatsApp number is not confirmed yet: its messages must not reach the platform. */
    public boolean isPending(String phone) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM platform_person_deletion WHERE completed_at IS NULL AND external_user_id = ?",
                Integer.class, PersonRefs.whatsappId(phone));
        return n != null && n > 0;
    }

    private void sendSoon() {
        try {
            executor.accept(this::sendDue);
        } catch (RuntimeException e) {
            log.warn("Could not start the platform person deletion now (the scan retries): {}", e.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${dadcoach.platform-person-deletion.initial-delay:PT1M}",
            fixedDelayString = "${dadcoach.platform-person-deletion.interval:PT5M}",
            scheduler = SchedulingLanes.HOUSEKEEPING)
    public void sendPeriodically() {
        sendDue();
    }

    /** Sends every due request. @return how many the platform confirmed */
    public synchronized int sendDue() {
        if (!enabled) {
            return 0;
        }
        int confirmed = 0;
        for (Pending pending : due()) {
            if (send(pending)) {
                confirmed++;
            }
        }
        return confirmed;
    }

    private record Pending(long fatherId, String personRef, String externalUserId, boolean purgeLocal, int attempts) {
    }

    private List<Pending> due() {
        return jdbc.query("""
                SELECT father_id, person_ref, external_user_id, purge_local, attempts FROM platform_person_deletion
                WHERE completed_at IS NULL AND next_attempt_at <= ? ORDER BY next_attempt_at, requested_at LIMIT ?""",
                (rs, i) -> new Pending(rs.getLong("father_id"), rs.getString("person_ref"), rs.getString("external_user_id"),
                        rs.getBoolean("purge_local"), rs.getInt("attempts")),
                Timestamp.from(clock.instant()), BATCH);
    }

    private boolean send(Pending pending) {
        String outcome;
        try {
            if (pending.externalUserId() != null) {
                PlatformTenancyClient.PeopleResult registered = platform.upsertPerson(new PlatformTenancyClient.PersonUpsert(
                        pending.personRef(), pending.externalUserId(), "whatsapp", null, "INACTIVE"));
                if (registered != null && registered.conflicts() != null && !registered.conflicts().isEmpty()) {
                    // e.g. the number belongs to another person ref there: never guessed - retried and logged
                    retryLater(pending, "register: " + registered.conflicts().get(0).reason());
                    return false;
                }
            }
            PlatformTenancyClient.PersonDeletionResult result = platform.deletePerson(pending.personRef());
            outcome = result == null || result.outcome() == null ? "DELETED" : result.outcome();
            log.info("The platform deleted the father's person: fatherId={}, outcome={}, workflowInstances={}, messages={}, attempts={}",
                    pending.fatherId(), outcome, result == null ? 0 : result.workflowInstances(),
                    result == null ? 0 : result.messages(), pending.attempts() + 1);
        } catch (WebClientResponseException e) {
            retryLater(pending, e.getStatusCode().value() + " " + code(e.getResponseBodyAsString()));
            return false;
        } catch (RuntimeException e) {
            retryLater(pending, e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
        if (pending.purgeLocal()) {
            try {
                purger.purge(pending.fatherId());
            } catch (RuntimeException e) {
                // the platform side is done (idempotent); the next attempt repeats it and the purge
                retryLater(pending, "local purge: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                return false;
            }
        }
        jdbc.update("""
                UPDATE platform_person_deletion SET completed_at = ?, outcome = ?, attempts = attempts + 1, last_error = NULL,
                       external_user_id = NULL WHERE father_id = ?""",
                Timestamp.from(clock.instant()), outcome, pending.fatherId());
        return true;
    }

    private void retryLater(Pending pending, String error) {
        int attempts = pending.attempts() + 1;
        Instant next = clock.instant().plus(backoff(attempts));
        String detail = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        jdbc.update("UPDATE platform_person_deletion SET attempts = ?, next_attempt_at = ?, last_error = ? WHERE father_id = ?",
                attempts, Timestamp.from(next), detail, pending.fatherId());
        if (attempts >= OVERDUE_ATTEMPTS) {
            log.error("Platform person deletion OVERDUE (still retried): fatherId={}, attempts={}, next={}, error={}",
                    pending.fatherId(), attempts, next, detail);
        } else {
            log.warn("Platform person deletion failed, retried later: fatherId={}, attempts={}, next={}, error={}",
                    pending.fatherId(), attempts, next, detail);
        }
    }

    static Duration backoff(int attempts) {
        Duration backoff = Duration.ofMinutes(1L << Math.min(Math.max(attempts - 1, 0), 20));
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    private static String code(String body) {
        if (body == null) {
            return "";
        }
        Matcher m = CODE.matcher(body);
        return m.find() ? m.group(1) : "";
    }
}
