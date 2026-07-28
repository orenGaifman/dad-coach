package com.dadcoach.workspace.growth.celebration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing celebration events in the growth system.
 *
 * <p>Celebrations are triggered when a father achieves belt level-ups, earns achievements,
 * reaches milestones, or hits streak milestones. Each celebration can include encouragement
 * metadata (celebration message and motivational prompt) generated asynchronously via the
 * Intelligence Layer.</p>
 *
 * <p>Requirements: 14.2, 14.3, 14.4</p>
 */
@Service
@Transactional
public class CelebrationEventService {

    private static final Logger log = LoggerFactory.getLogger(CelebrationEventService.class);

    private final CelebrationEventRepository celebrationEventRepository;

    public CelebrationEventService(CelebrationEventRepository celebrationEventRepository) {
        this.celebrationEventRepository = celebrationEventRepository;
    }

    /**
     * Creates a new celebration event and persists it.
     *
     * <p>TODO: Trigger async encouragement metadata generation via Intelligence Layer.
     * The celebration message and motivational prompt will be populated asynchronously
     * after the event is persisted.</p>
     *
     * @param type          the type of celebration event
     * @param fatherId      the father's unique identifier
     * @param title         the celebration title
     * @param description   the celebration description
     * @param relatedPoints the related growth signal points (nullable)
     * @return the persisted celebration event
     */
    public CelebrationEvent createCelebration(CelebrationEventType type, UUID fatherId,
                                              String title, String description,
                                              Integer relatedPoints) {
        CelebrationEvent event = new CelebrationEvent(fatherId, type, title, description, relatedPoints);
        CelebrationEvent saved = celebrationEventRepository.save(event);

        log.info("Created celebration event [{}] for father {} — type={}, title='{}'",
                saved.getEventId(), fatherId, type, title);

        // TODO: Emit async event for Intelligence Layer to generate encouragement metadata
        // e.g., applicationEventPublisher.publishEvent(new CelebrationCreatedEvent(saved));
        // The Intelligence Layer would then set celebrationMessage and motivationalPrompt.

        return saved;
    }

    /**
     * Retrieves all undisplayed celebration events for a father.
     *
     * @param fatherId the father's unique identifier
     * @return list of celebrations not yet shown to the father
     */
    @Transactional(readOnly = true)
    public List<CelebrationEvent> getUndisplayed(UUID fatherId) {
        return celebrationEventRepository.findByFatherIdAndDisplayedFalse(fatherId);
    }

    /**
     * Marks the specified celebration events as displayed for the given father.
     *
     * <p>Only events belonging to the specified father are updated (ownership enforcement).</p>
     *
     * @param fatherId the father's unique identifier
     * @param eventIds the IDs of events to mark as displayed
     */
    public void markDisplayed(UUID fatherId, List<UUID> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }

        List<CelebrationEvent> events = celebrationEventRepository.findAllById(eventIds);
        int markedCount = 0;

        for (CelebrationEvent event : events) {
            if (event.getFatherId().equals(fatherId) && !event.isDisplayed()) {
                event.setDisplayed(true);
                celebrationEventRepository.save(event);
                markedCount++;
            }
        }

        log.debug("Marked {} celebration events as displayed for father {}", markedCount, fatherId);
    }
}
