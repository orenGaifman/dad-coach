package com.dadcoach.workspace.growth.belt;

import com.dadcoach.workspace.dto.response.BeltProgressionResponse;

import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for managing belt level transitions.
 *
 * <p>Belt progression is monotonic (Design Decision AD-8): once a father reaches
 * a belt level, they retain it permanently regardless of subsequent score changes.
 * The service evaluates whether a father's current score qualifies them for a
 * higher belt and performs the promotion if so.</p>
 *
 * @see BeltLevel
 * @see BeltThreshold
 * @see FatherBelt
 */
public interface BeltProgressionService {

    /**
     * Returns the current belt record for a father, creating a default WHITE belt if none exists.
     *
     * @param fatherId the father's unique identifier
     * @return the father's current belt record
     */
    FatherBelt getCurrentBelt(UUID fatherId);

    /**
     * Returns the full belt progression response for a father, including current belt,
     * score, next belt, points remaining, and progress percentage.
     *
     * @param fatherId the father's unique identifier
     * @return the belt progression response DTO
     */
    BeltProgressionResponse getProgression(UUID fatherId);

    /**
     * Evaluates whether the father's current score qualifies them for a belt promotion.
     *
     * <p>Compares the score against belt thresholds and the father's current belt level.
     * Returns the new belt level if a promotion is warranted, or empty if the father
     * already holds the correct (or higher) belt for their score.</p>
     *
     * <p>Belt monotonicity is enforced: this method will never return a belt lower
     * than the father's current belt.</p>
     *
     * @param fatherId     the father's unique identifier
     * @param currentScore the father's current total growth score
     * @return the new belt level if promotion is warranted, or empty if no promotion needed
     */
    Optional<BeltLevel> evaluatePromotion(UUID fatherId, int currentScore);

    /**
     * Promotes a father to the specified new belt level.
     *
     * <p>Updates the father's belt record, sets belt_earned_at timestamp, and publishes
     * a {@link com.dadcoach.workspace.event.BeltLevelUpEvent}. This method enforces
     * monotonicity — it will not downgrade the belt.</p>
     *
     * @param fatherId the father's unique identifier
     * @param newBelt  the target belt level (must be higher than current)
     * @throws IllegalStateException if newBelt is not higher than the current belt
     */
    void promoteBelt(UUID fatherId, BeltLevel newBelt);
}
