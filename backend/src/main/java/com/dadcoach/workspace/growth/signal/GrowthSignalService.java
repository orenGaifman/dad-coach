package com.dadcoach.workspace.growth.signal;

import com.dadcoach.workspace.DuplicateSignalException;
import com.dadcoach.workspace.growth.score.ScoringPolicyVersion;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for recording and querying growth signals in the Father Growth System.
 *
 * <p>Growth signals are immutable event records representing positive actions that
 * contribute to a father's Growth_Score. This service handles:</p>
 * <ul>
 *   <li>Signal recording with duplicate detection (Requirement 11.6)</li>
 *   <li>Score breakdown by signal type</li>
 *   <li>Time-range queries for reporting</li>
 *   <li>Scoring policy version tagging (AD-7)</li>
 * </ul>
 *
 * <p>Duplicate detection uses the database unique constraint on
 * {@code (father_id, signal_type, source_entity_id)}. The service performs a
 * pre-check before insert to provide a clear exception rather than relying on
 * constraint violation handling.</p>
 *
 * @see GrowthSignal
 * @see GrowthSignalType
 * @see SignalWeight
 * @see ScoringPolicyVersion
 */
@Service
public class GrowthSignalService {

    private final GrowthSignalRepository growthSignalRepository;

    public GrowthSignalService(GrowthSignalRepository growthSignalRepository) {
        this.growthSignalRepository = growthSignalRepository;
    }

    /**
     * Records a new growth signal for a father.
     *
     * <p>The method first checks for duplicates. If a signal with the same
     * (father_id, signal_type, source_entity_id) combination already exists,
     * a {@link DuplicateSignalException} is thrown. Otherwise, a new immutable
     * signal is created with the configured point value and current scoring policy version.</p>
     *
     * @param type             the type of growth signal
     * @param fatherId         the father's unique identifier
     * @param sourceEntityId   the source entity that triggered this signal
     * @param sourceEntityType a label describing the source entity type (e.g., "mission", "goal")
     * @return the persisted growth signal
     * @throws DuplicateSignalException if a signal for this source event already exists
     */
    @Transactional
    public GrowthSignal recordSignal(GrowthSignalType type, UUID fatherId,
                                     UUID sourceEntityId, String sourceEntityType) {
        if (isDuplicate(type, fatherId, sourceEntityId)) {
            throw new DuplicateSignalException(sourceEntityId.toString());
        }

        int points = SignalWeight.getPoints(type);

        GrowthSignal signal = GrowthSignal.builder()
                .fatherId(fatherId)
                .signalType(type)
                .pointsAwarded(points)
                .sourceEntityId(sourceEntityId)
                .sourceEntityType(sourceEntityType)
                .scoringPolicyVersion(ScoringPolicyVersion.CURRENT)
                .createdAt(Instant.now())
                .build();

        return growthSignalRepository.save(signal);
    }

    /**
     * Checks whether a growth signal has already been recorded for the given combination.
     *
     * @param type           the growth signal type
     * @param fatherId       the father's unique identifier
     * @param sourceEntityId the source entity identifier
     * @return true if a matching signal already exists
     */
    public boolean isDuplicate(GrowthSignalType type, UUID fatherId, UUID sourceEntityId) {
        return growthSignalRepository.existsByFatherIdAndSignalTypeAndSourceEntityId(
                fatherId, type, sourceEntityId);
    }

    /**
     * Retrieves the most recent growth signals for a father, limited to the specified count.
     *
     * @param fatherId the father's unique identifier
     * @param limit    maximum number of signals to return (must be positive)
     * @return list of the most recent signals, ordered by creation time descending
     */
    public List<GrowthSignal> getRecentSignals(UUID fatherId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return growthSignalRepository.findByFatherIdOrderByCreatedAtDesc(fatherId, pageable)
                .getContent();
    }

    /**
     * Returns a score breakdown grouped by signal type for a father.
     *
     * <p>Each entry maps a {@link GrowthSignalType} to the total points accumulated
     * from that signal type. Signal types with no recorded signals are omitted.</p>
     *
     * @param fatherId the father's unique identifier
     * @return map of signal type to total points for that type
     */
    public Map<GrowthSignalType, Integer> getScoreBreakdown(UUID fatherId) {
        List<GrowthSignal> signals = growthSignalRepository.findByFatherIdOrderByCreatedAtDesc(fatherId);
        return signals.stream()
                .collect(Collectors.groupingBy(
                        GrowthSignal::getSignalType,
                        Collectors.summingInt(GrowthSignal::getPointsAwarded)
                ));
    }

    /**
     * Computes the total growth score for a father by summing all awarded points.
     * This delegates to the repository's authoritative SUM query (Design Decision AD-9).
     *
     * @param fatherId the father's unique identifier
     * @return the total growth score (0 if no signals exist)
     */
    public int getTotalScore(UUID fatherId) {
        return growthSignalRepository.sumPointsByFatherId(fatherId);
    }

    /**
     * Retrieves all growth signals for a father within a specific time period.
     *
     * @param fatherId the father's unique identifier
     * @param from     the start of the time range (inclusive)
     * @param to       the end of the time range (inclusive)
     * @return list of signals within the specified time range
     */
    public List<GrowthSignal> getSignalsInPeriod(UUID fatherId, Instant from, Instant to) {
        return growthSignalRepository.findByFatherIdAndCreatedAtBetween(fatherId, from, to);
    }
}
