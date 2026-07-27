package com.dadcoach.scheduling;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.statemachine.StateMachineEngine;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Service that detects inactive fathers and applies inactivity-driven transitions.
 *
 * <p>Inactivity thresholds per Requirements 10.4-10.7:</p>
 * <ul>
 *   <li>3 days → INACTIVITY_CHECK notification (warm, low-pressure)</li>
 *   <li>7 days → second check referencing specific child or memory</li>
 *   <li>14 days → final re-engagement with emotional content</li>
 *   <li>21 days → transition to CHURNED, cease all outbound messages</li>
 * </ul>
 */
@Service
@Transactional
public class InactivityService {

    public static final int THRESHOLD_FIRST_CHECK = 3;
    public static final int THRESHOLD_SECOND_CHECK = 7;
    public static final int THRESHOLD_FINAL_CHECK = 14;
    public static final int THRESHOLD_CHURNED = 21;

    private final FatherRepository fatherRepository;
    private final StateMachineEngine stateMachineEngine;

    public InactivityService(FatherRepository fatherRepository, StateMachineEngine stateMachineEngine) {
        this.fatherRepository = fatherRepository;
        this.stateMachineEngine = stateMachineEngine;
    }

    /**
     * Detects inactive fathers at the 21-day threshold and transitions them to CHURNED.
     *
     * <p>Per Requirement 1.4 / Property 9: ACTIVE Father with last_interaction_at > 21 days → CHURNED.</p>
     *
     * @param now the current time reference
     * @return the list of fathers that were transitioned to CHURNED
     */
    public List<Father> processChurnedFathers(Instant now) {
        Instant threshold = now.minus(Duration.ofDays(THRESHOLD_CHURNED));
        List<Father> inactiveFathers = fatherRepository.findByStatusAndLastInteractionAtBefore(
                FatherStatus.ACTIVE, threshold);

        for (Father father : inactiveFathers) {
            churnFather(father);
        }

        return inactiveFathers;
    }

    /**
     * Transitions a single father to CHURNED status.
     *
     * @param father the father to churn (must be ACTIVE)
     */
    public void churnFather(Father father) {
        if (father.getStatus() != FatherStatus.ACTIVE) {
            return; // Only ACTIVE fathers can be churned
        }
        stateMachineEngine.transition(
                "Father", father.getId(), father.getStatus(), FatherStatus.CHURNED,
                "Inactive for 21+ days"
        );
        father.setStatus(FatherStatus.CHURNED);
        fatherRepository.save(father);
    }

    /**
     * Determines the current inactivity level for a father.
     *
     * @param father the father to check
     * @param now    the current time reference
     * @return the inactivity level (0 = active, 3/7/14/21 = threshold crossed)
     */
    public int getInactivityLevel(Father father, Instant now) {
        if (father.getLastInteractionAt() == null) {
            // Use creation time as baseline
            if (father.getCreatedAt() == null) {
                return 0;
            }
            long daysSinceCreation = Duration.between(father.getCreatedAt(), now).toDays();
            return classifyInactivityDays(daysSinceCreation);
        }

        long daysSinceInteraction = Duration.between(father.getLastInteractionAt(), now).toDays();
        return classifyInactivityDays(daysSinceInteraction);
    }

    /**
     * Classifies the number of inactive days into the threshold levels.
     *
     * @param days the number of days inactive
     * @return the highest threshold crossed (21, 14, 7, 3, or 0)
     */
    public static int classifyInactivityDays(long days) {
        if (days >= THRESHOLD_CHURNED) {
            return THRESHOLD_CHURNED;
        } else if (days >= THRESHOLD_FINAL_CHECK) {
            return THRESHOLD_FINAL_CHECK;
        } else if (days >= THRESHOLD_SECOND_CHECK) {
            return THRESHOLD_SECOND_CHECK;
        } else if (days >= THRESHOLD_FIRST_CHECK) {
            return THRESHOLD_FIRST_CHECK;
        }
        return 0;
    }
}
