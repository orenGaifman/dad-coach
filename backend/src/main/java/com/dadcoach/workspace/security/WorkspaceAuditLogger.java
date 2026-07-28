package com.dadcoach.workspace.security;

import com.dadcoach.workspace.event.AchievementEarnedEvent;
import com.dadcoach.workspace.event.BeltLevelUpEvent;
import com.dadcoach.workspace.event.GrowthSignalRecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit logger for workspace access and growth system mutations.
 *
 * <p>Logs structured audit entries for:</p>
 * <ul>
 *   <li>API endpoint access (actor_type, actor_id, endpoint, target_father_id, timestamp, result)</li>
 *   <li>Growth mutations: signal_recorded, achievement_earned, belt_level_up</li>
 * </ul>
 *
 * <p>Sensitive data (phone numbers, tokens) is excluded from logs.</p>
 */
@Component
public class WorkspaceAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("workspace.audit");

    /**
     * Logs an API access event.
     *
     * @param actorType      the type of actor (FATHER, ADMIN, SERVICE)
     * @param actorId        the actor's identifier
     * @param endpoint       the accessed endpoint path
     * @param targetFatherId the father ID whose data is being accessed
     * @param result         the result status (SUCCESS, DENIED, ERROR)
     */
    public void logAccess(String actorType, UUID actorId, String endpoint,
                          UUID targetFatherId, AuditResult result) {
        auditLog.info("action=workspace_access actor_type={} actor_id={} endpoint={} " +
                        "target_father_id={} result={} timestamp={}",
                actorType,
                actorId,
                endpoint,
                targetFatherId,
                result,
                Instant.now());
    }

    /**
     * Logs an API access event with an HTTP method.
     *
     * @param actorType      the type of actor
     * @param actorId        the actor's identifier
     * @param method         the HTTP method (GET, POST, etc.)
     * @param endpoint       the accessed endpoint path
     * @param targetFatherId the father ID whose data is being accessed
     * @param result         the result status
     */
    public void logAccess(String actorType, UUID actorId, String method, String endpoint,
                          UUID targetFatherId, AuditResult result) {
        auditLog.info("action=workspace_access actor_type={} actor_id={} method={} endpoint={} " +
                        "target_father_id={} result={} timestamp={}",
                actorType,
                actorId,
                method,
                endpoint,
                targetFatherId,
                result,
                Instant.now());
    }

    @EventListener
    public void onGrowthSignalRecorded(GrowthSignalRecordedEvent event) {
        auditLog.info("action=signal_recorded father_id={} signal_type={} points={} " +
                        "new_total_score={} timestamp={}",
                event.getFatherId(),
                event.getSignalType(),
                event.getPointsAwarded(),
                event.getNewTotalScore(),
                event.getOccurredAt());
    }

    @EventListener
    public void onAchievementEarned(AchievementEarnedEvent event) {
        auditLog.info("action=achievement_earned father_id={} achievement_id={} timestamp={}",
                event.getFatherId(),
                event.getAchievementId(),
                event.getOccurredAt());
    }

    @EventListener
    public void onBeltLevelUp(BeltLevelUpEvent event) {
        auditLog.info("action=belt_level_up father_id={} new_belt={} score={} timestamp={}",
                event.getFatherId(),
                event.getNewBelt(),
                event.getCurrentScore(),
                event.getOccurredAt());
    }

    /**
     * Audit result status.
     */
    public enum AuditResult {
        SUCCESS, DENIED, ERROR, RATE_LIMITED
    }
}
