package com.dadcoach.workspace;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.workspace.aggregation.ChildrenOverviewService;
import com.dadcoach.workspace.dto.response.ChildSummaryResponse;
import com.dadcoach.workspace.dto.response.ChildrenOverviewResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for children-related workspace endpoints.
 *
 * <p>Provides an overview of all children for a father and detailed summary
 * for individual children. Enforces ownership — returns 404 for children
 * belonging to other fathers.</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/children")
public class ChildrenOverviewController {

    private final ChildrenOverviewService childrenOverviewService;

    public ChildrenOverviewController(ChildrenOverviewService childrenOverviewService) {
        this.childrenOverviewService = childrenOverviewService;
    }

    /**
     * Returns an overview of all children for the authenticated father.
     *
     * <p>Includes per-child metrics: age, active goals, completed missions,
     * recent mission, and interests.</p>
     *
     * @param actor the authenticated actor context
     * @return 200 OK with the children overview response
     */
    @GetMapping
    public ResponseEntity<ChildrenOverviewResponse> getChildrenOverview(@AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        ChildrenOverviewResponse response = childrenOverviewService.getChildrenOverview(fatherId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns a detailed summary for a specific child.
     *
     * <p>Includes goals, mission history, interests, and upcoming birthday indicator.
     * Enforces ownership: returns 404 if the child does not belong to the authenticated father.</p>
     *
     * @param childId the child's unique identifier
     * @param actor   the authenticated actor context
     * @return 200 OK with the child summary response, or 404 if not found/not owned
     */
    @GetMapping("/{childId}/summary")
    public ResponseEntity<ChildSummaryResponse> getChildSummary(
            @PathVariable UUID childId,
            @AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        ChildSummaryResponse response = childrenOverviewService.getChildSummary(fatherId, childId);
        return ResponseEntity.ok(response);
    }
}
