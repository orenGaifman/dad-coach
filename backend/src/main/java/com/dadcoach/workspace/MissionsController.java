package com.dadcoach.workspace;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.workspace.aggregation.MissionsOverviewService;
import com.dadcoach.workspace.dto.response.ActiveMissionsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for mission-related workspace endpoints.
 *
 * <p>Provides active missions overview for the authenticated father.</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/missions")
public class MissionsController {

    private final MissionsOverviewService missionsOverviewService;

    public MissionsController(MissionsOverviewService missionsOverviewService) {
        this.missionsOverviewService = missionsOverviewService;
    }

    /**
     * Returns all active missions for the authenticated father.
     *
     * <p>Active missions include those with status: ASSIGNED, ACCEPTED, IN_PROGRESS.</p>
     *
     * @param actor the authenticated actor context
     * @return 200 OK with active missions response
     */
    @GetMapping("/active")
    public ResponseEntity<ActiveMissionsResponse> getActiveMissions(@AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        ActiveMissionsResponse response = missionsOverviewService.getActiveMissions(fatherId);
        return ResponseEntity.ok(response);
    }
}
