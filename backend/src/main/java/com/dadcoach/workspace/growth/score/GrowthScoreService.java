package com.dadcoach.workspace.growth.score;

import com.dadcoach.workspace.growth.belt.FatherBelt;
import com.dadcoach.workspace.growth.belt.FatherBeltRepository;
import com.dadcoach.workspace.growth.signal.GrowthSignalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing and querying the father's cached growth score.
 *
 * <p>The Growth System uses two sources for score data (Design Decision AD-9):</p>
 * <ul>
 *   <li><strong>Authoritative source:</strong> {@code SUM(growth_signals.points_awarded)} —
 *       always correct, but requires a full aggregation query.</li>
 *   <li><strong>Cached read-model:</strong> {@code father_belts.current_score} — updated
 *       incrementally on each signal recording for fast reads.</li>
 * </ul>
 *
 * <p>This service provides three operations:</p>
 * <ul>
 *   <li>{@link #getTotalScore(UUID)} — Reads the cached score from father_belts for fast access.</li>
 *   <li>{@link #rebuildScore(UUID)} — Reconciles the cached score with the authoritative SUM.</li>
 *   <li>{@link #incrementScore(UUID, int)} — Atomically increments the cached score after
 *       a new signal is recorded.</li>
 * </ul>
 *
 * @see com.dadcoach.workspace.growth.signal.GrowthSignalRepository
 * @see FatherBeltRepository
 * @see ScoringPolicyVersion
 */
@Service
public class GrowthScoreService {

    private final FatherBeltRepository fatherBeltRepository;
    private final GrowthSignalRepository growthSignalRepository;

    public GrowthScoreService(FatherBeltRepository fatherBeltRepository,
                              GrowthSignalRepository growthSignalRepository) {
        this.fatherBeltRepository = fatherBeltRepository;
        this.growthSignalRepository = growthSignalRepository;
    }

    /**
     * Reads the cached total growth score for a father from the father_belts table.
     *
     * <p>If no belt record exists yet for this father, a new one is created with
     * WHITE belt and score 0, then 0 is returned.</p>
     *
     * @param fatherId the father's unique identifier
     * @return the cached total growth score (0 if newly created)
     */
    @Transactional
    public int getTotalScore(UUID fatherId) {
        return fatherBeltRepository.findByFatherId(fatherId)
                .map(FatherBelt::getCurrentScore)
                .orElseGet(() -> {
                    fatherBeltRepository.save(new FatherBelt(fatherId));
                    return 0;
                });
    }

    /**
     * Rebuilds the cached score by computing SUM(points_awarded) from growth_signals
     * and updating the father_belts.current_score to match.
     *
     * <p>This is a reconciliation operation used to correct the cached score if it
     * diverges from the authoritative signal store (e.g., due to a failed incremental
     * update or a bug). Belt transitions should be evaluated against this rebuilt value.</p>
     *
     * <p>If no belt record exists for the father, one is created with the computed score.</p>
     *
     * @param fatherId the father's unique identifier
     * @return the rebuilt (authoritative) total score
     */
    @Transactional
    public int rebuildScore(UUID fatherId) {
        int authoritativeScore = growthSignalRepository.sumPointsByFatherId(fatherId);

        FatherBelt belt = fatherBeltRepository.findByFatherId(fatherId)
                .orElseGet(() -> {
                    FatherBelt newBelt = new FatherBelt(fatherId);
                    return fatherBeltRepository.save(newBelt);
                });

        belt.setCurrentScore(authoritativeScore);
        fatherBeltRepository.save(belt);

        return authoritativeScore;
    }

    /**
     * Atomically increments the cached current_score for a father after a new signal
     * is recorded.
     *
     * <p>Uses an atomic UPDATE query ({@link FatherBeltRepository#incrementScore}) to
     * avoid read-then-write race conditions under concurrent signal recording.</p>
     *
     * <p>If no belt record exists for the father, a new one is created with the given
     * points as the initial score.</p>
     *
     * @param fatherId the father's unique identifier
     * @param points   the number of points to add (must be positive)
     */
    @Transactional
    public void incrementScore(UUID fatherId, int points) {
        int updatedRows = fatherBeltRepository.incrementScore(fatherId, points);

        if (updatedRows == 0) {
            // No belt record exists yet — create one with the initial points
            FatherBelt newBelt = new FatherBelt(fatherId);
            newBelt.setCurrentScore(points);
            fatherBeltRepository.save(newBelt);
        }
    }
}
