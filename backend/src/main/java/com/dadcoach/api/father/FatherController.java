package com.dadcoach.api.father;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.domain.father.Father;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Father self-service API controller.
 * <p>
 * Provides endpoints for a father to manage their own profile and preferences.
 * All operations are scoped to the authenticated father (via /me pattern).
 * <p>
 * This controller NEVER accesses the database directly — all operations
 * are delegated to {@link FatherApiService}.
 * <p>
 * Response DTOs are mapped via {@link FatherMapper} to ensure sensitive fields
 * (embeddings, AI prompts, raw confidence scores, phone) are never exposed.
 */
@RestController
@RequestMapping("/api/v1/fathers/me")
public class FatherController {

    private final FatherApiService fatherApiService;
    private final FatherMapper fatherMapper;

    public FatherController(FatherApiService fatherApiService, FatherMapper fatherMapper) {
        this.fatherApiService = fatherApiService;
        this.fatherMapper = fatherMapper;
    }

    /**
     * GET /api/v1/fathers/me — Retrieve the authenticated father's profile.
     * <p>
     * Returns public fields only: display_name, timezone, coaching_style,
     * preferred_coaching_time, status, phase, engagement_score, coaching_streak.
     *
     * @param actor the authenticated actor context (injected via @AuthActor)
     * @return the father's public profile
     */
    @GetMapping
    public ResponseEntity<FatherResponseDto> getProfile(@AuthActor ActorContext actor) {
        Father father = fatherApiService.getProfile(actor.getActorId());
        FatherResponseDto response = fatherMapper.toDto(father);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/v1/fathers/me — Update the authenticated father's preferences.
     * <p>
     * Updatable fields:
     * <ul>
     *   <li>timezone — must be a valid IANA timezone identifier</li>
     *   <li>coachingStyle — one of GENTLE, BALANCED, DIRECT, MOTIVATIONAL</li>
     *   <li>preferredCoachingTime — must be in HH:MM format (24-hour)</li>
     * </ul>
     * <p>
     * Only non-null fields in the request body are applied (partial update).
     *
     * @param actor   the authenticated actor context
     * @param request the validated update request
     * @return the updated father profile
     */
    @PutMapping
    public ResponseEntity<FatherResponseDto> updatePreferences(
            @AuthActor ActorContext actor,
            @Valid @RequestBody FatherUpdateRequest request) {
        Father updated = fatherApiService.updatePreferences(actor.getActorId(), request);
        FatherResponseDto response = fatherMapper.toDto(updated);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/fathers/me — Request GDPR account deletion.
     * <p>
     * This initiates the deletion flow. The account is not immediately removed;
     * a grace period applies per GDPR requirements. The actual data purge is
     * handled asynchronously by the deletion pipeline.
     * <p>
     * Returns 202 Accepted to indicate the deletion request has been received
     * and will be processed asynchronously.
     *
     * @param actor the authenticated actor context
     * @return 202 Accepted with no body
     */
    @DeleteMapping
    public ResponseEntity<Void> requestDeletion(@AuthActor ActorContext actor) {
        fatherApiService.requestDeletion(actor.getActorId());
        return ResponseEntity.accepted().build();
    }
}
