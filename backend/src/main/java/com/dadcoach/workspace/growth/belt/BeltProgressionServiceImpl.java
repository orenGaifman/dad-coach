package com.dadcoach.workspace.growth.belt;

import com.dadcoach.workspace.dto.response.BeltProgressionResponse;
import com.dadcoach.workspace.event.BeltLevelUpEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link BeltProgressionService}.
 *
 * <p>Manages belt transitions with monotonicity enforcement (Design Decision AD-8).
 * A father's belt level never regresses — once earned, a belt is retained permanently.</p>
 *
 * <p>On promotion, this service updates the FatherBelt entity and publishes a
 * {@link BeltLevelUpEvent} for downstream processing (celebration events, activity feed,
 * cache invalidation).</p>
 *
 * @see BeltThreshold
 * @see BeltLevel
 */
@Service
public class BeltProgressionServiceImpl implements BeltProgressionService {

    private static final Logger log = LoggerFactory.getLogger(BeltProgressionServiceImpl.class);

    private final FatherBeltRepository fatherBeltRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BeltProgressionServiceImpl(FatherBeltRepository fatherBeltRepository,
                                      ApplicationEventPublisher eventPublisher) {
        this.fatherBeltRepository = fatherBeltRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public FatherBelt getCurrentBelt(UUID fatherId) {
        return fatherBeltRepository.findByFatherId(fatherId)
                .orElseGet(() -> {
                    log.info("Creating initial belt record for father={}", fatherId);
                    FatherBelt newBelt = new FatherBelt(fatherId);
                    return fatherBeltRepository.save(newBelt);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public BeltProgressionResponse getProgression(UUID fatherId) {
        FatherBelt belt = getCurrentBelt(fatherId);
        BeltLevel currentBeltLevel = belt.getBeltLevel();
        int currentScore = belt.getCurrentScore();

        BeltLevel nextBelt = currentBeltLevel.nextBelt();
        int pointsToNext = BeltThreshold.getPointsToNextBelt(currentBeltLevel, currentScore);
        int progressPercentage = BeltThreshold.getProgressPercentage(currentBeltLevel, currentScore);

        log.debug("Belt progression for father={}: belt={}, score={}, next={}, pointsRemaining={}, progress={}%",
                fatherId, currentBeltLevel, currentScore, nextBelt, pointsToNext, progressPercentage);

        BeltProgressionResponse.Builder builder = BeltProgressionResponse.builder()
                .currentBelt(currentBeltLevel.name())
                .currentBeltDescription(currentBeltLevel.getDescription())
                .currentScore(currentScore)
                .pointsToNextBelt(pointsToNext)
                .progressPercentageToNextBelt(progressPercentage)
                .beltEarnedAt(belt.getBeltEarnedAt());

        if (nextBelt != null) {
            builder.nextBelt(nextBelt.name())
                    .nextBeltDescription(nextBelt.getDescription());
        }

        return builder.build();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BeltLevel> evaluatePromotion(UUID fatherId, int currentScore) {
        FatherBelt fatherBelt = fatherBeltRepository.findByFatherId(fatherId).orElse(null);

        if (fatherBelt == null) {
            // No belt record yet — if score qualifies for something above WHITE, return it
            BeltLevel deservedBelt = BeltThreshold.beltForScore(currentScore);
            if (deservedBelt.isHigherThan(BeltLevel.WHITE)) {
                log.info("Belt promotion eligible (no record) for father={}: WHITE → {} (score={})",
                        fatherId, deservedBelt, currentScore);
                return Optional.of(deservedBelt);
            }
            return Optional.empty();
        }

        BeltLevel currentBelt = fatherBelt.getBeltLevel();
        BeltLevel deservedBelt = BeltThreshold.beltForScore(currentScore);

        // Monotonicity enforcement: only promote, never demote
        if (deservedBelt.isHigherThan(currentBelt)) {
            log.info("Belt promotion eligible for father={}: {} → {} (score={})",
                    fatherId, currentBelt, deservedBelt, currentScore);
            return Optional.of(deservedBelt);
        }

        return Optional.empty();
    }

    @Override
    @Transactional
    public void promoteBelt(UUID fatherId, BeltLevel newBelt) {
        // Use pessimistic lock to prevent concurrent belt promotions from racing
        FatherBelt fatherBelt = fatherBeltRepository.findByFatherIdForUpdate(fatherId)
                .orElseGet(() -> {
                    log.info("Creating initial belt record for promotion, father={}", fatherId);
                    FatherBelt created = new FatherBelt(fatherId);
                    return fatherBeltRepository.save(created);
                });

        BeltLevel previousBelt = fatherBelt.getBeltLevel();

        // Monotonicity enforcement: refuse to downgrade
        if (!newBelt.isHigherThan(previousBelt)) {
            log.warn("Attempted belt downgrade for father={}: {} → {} — refused (monotonicity AD-8)",
                    fatherId, previousBelt, newBelt);
            throw new IllegalStateException(
                    "Belt monotonicity violation: cannot transition from " + previousBelt + " to " + newBelt +
                            ". Belt progression is monotonic (AD-8).");
        }

        fatherBelt.setBeltLevel(newBelt);
        fatherBelt.setBeltEarnedAt(Instant.now());
        fatherBeltRepository.save(fatherBelt);

        int currentScore = fatherBelt.getCurrentScore();

        log.info("Belt promoted for father={}: {} → {} (score={})",
                fatherId, previousBelt, newBelt, currentScore);

        eventPublisher.publishEvent(new BeltLevelUpEvent(fatherId, previousBelt, newBelt, currentScore));
    }
}
