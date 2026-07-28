package com.dadcoach.workspace;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.workspace.dto.request.MarkCelebrationDisplayedRequest;
import com.dadcoach.workspace.dto.response.CelebrationEventsResponse;
import com.dadcoach.workspace.growth.celebration.CelebrationEvent;
import com.dadcoach.workspace.growth.celebration.CelebrationEventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for celebration events endpoints.
 *
 * <p>Provides access to celebration events (belt level-ups, achievements earned,
 * milestones reached, streak milestones) and the ability to mark them as displayed.</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/growth/celebrations")
public class CelebrationController {

    private final CelebrationEventService celebrationEventService;

    public CelebrationController(CelebrationEventService celebrationEventService) {
        this.celebrationEventService = celebrationEventService;
    }

    /**
     * Returns celebration events for the authenticated father.
     *
     * @param principal       the authenticated user
     * @param undisplayedOnly if true (default), returns only undisplayed celebrations
     * @return 200 OK with celebration events response
     */
    @GetMapping
    public ResponseEntity<CelebrationEventsResponse> getCelebrations(
            @AuthActor ActorContext actor,
            @RequestParam(name = "undisplayed_only", defaultValue = "true") boolean undisplayedOnly) {

        UUID fatherId = actor.getActorId();
        List<CelebrationEvent> events = celebrationEventService.getUndisplayed(fatherId);

        List<CelebrationEventsResponse.CelebrationItem> items = events.stream()
                .map(this::toCelebrationItem)
                .toList();

        return ResponseEntity.ok(new CelebrationEventsResponse(items));
    }

    /**
     * Marks the specified celebration events as displayed.
     *
     * @param principal the authenticated user
     * @param request   the request containing event IDs to mark
     * @return 200 OK on success
     */
    @PostMapping("/mark-displayed")
    public ResponseEntity<Void> markDisplayed(
            @AuthActor ActorContext actor,
            @Valid @RequestBody MarkCelebrationDisplayedRequest request) {

        UUID fatherId = actor.getActorId();
        celebrationEventService.markDisplayed(fatherId, request.getEventIds());
        return ResponseEntity.ok().build();
    }

    private CelebrationEventsResponse.CelebrationItem toCelebrationItem(CelebrationEvent event) {
        return new CelebrationEventsResponse.CelebrationItem(
                event.getEventId(),
                event.getEventType().name(),
                event.getTitle(),
                event.getDescription(),
                event.getRelatedGrowthSignalPoints(),
                event.getCelebrationMessage(),
                event.getMotivationalPrompt(),
                event.getCreatedAt()
        );
    }
}
