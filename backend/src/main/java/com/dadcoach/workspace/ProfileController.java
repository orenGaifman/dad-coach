package com.dadcoach.workspace;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.workspace.aggregation.ProfileReadService;
import com.dadcoach.workspace.dto.response.ProfileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for the workspace profile endpoint.
 *
 * <p>Provides read-only access to the father's profile data including
 * masked phone, coaching preferences, and computed metrics.</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class ProfileController {

    private final ProfileReadService profileReadService;

    public ProfileController(ProfileReadService profileReadService) {
        this.profileReadService = profileReadService;
    }

    /**
     * Returns the profile data for the authenticated father.
     *
     * @param actor the authenticated actor context
     * @return 200 OK with the profile response
     */
    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile(@AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        ProfileResponse response = profileReadService.getProfile(fatherId);
        return ResponseEntity.ok(response);
    }
}
