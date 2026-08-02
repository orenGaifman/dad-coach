package com.dadcoach.workspace.commitment;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.workspace.commitment.CommitmentService.CommitmentStats;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for managing quality time commitments.
 */
@RestController
@RequestMapping("/api/v1/workspace/commitments")
public class CommitmentController {

    private final CommitmentService commitmentService;
    private final ChildRepository childRepository;

    public CommitmentController(CommitmentService commitmentService, ChildRepository childRepository) {
        this.commitmentService = commitmentService;
        this.childRepository = childRepository;
    }

    /**
     * Get all commitments for the authenticated father.
     */
    @GetMapping
    public ResponseEntity<List<CommitmentResponse>> getCommitments(@AuthActor ActorContext actor) {
        Long fatherId = extractFatherId(actor);
        List<QualityTimeCommitment> commitments = commitmentService.getAllCommitments(fatherId);
        List<CommitmentResponse> response = commitments.stream()
                .map(c -> toResponse(c))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get upcoming commitments.
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<CommitmentResponse>> getUpcomingCommitments(@AuthActor ActorContext actor) {
        Long fatherId = extractFatherId(actor);
        List<QualityTimeCommitment> commitments = commitmentService.getUpcomingCommitments(fatherId);
        List<CommitmentResponse> response = commitments.stream()
                .map(c -> toResponse(c))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get commitment statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats(@AuthActor ActorContext actor) {
        Long fatherId = extractFatherId(actor);
        CommitmentStats stats = commitmentService.getStats(fatherId);
        return ResponseEntity.ok(new StatsResponse(
                stats.completed(),
                stats.upcoming(),
                stats.missed(),
                stats.total(),
                stats.completionRate()
        ));
    }

    /**
     * Create a new commitment from the dashboard.
     */
    @PostMapping
    public ResponseEntity<CommitmentResponse> createCommitment(
            @AuthActor ActorContext actor,
            @RequestBody CreateCommitmentRequest request) {
        Long fatherId = extractFatherId(actor);
        QualityTimeCommitment commitment = commitmentService.createCommitment(
                fatherId,
                request.childId(),
                request.scheduledAt(),
                request.activityType(),
                request.activityNote(),
                "DASHBOARD",
                null
        );
        
        return ResponseEntity.ok(toResponse(commitment));
    }

    /**
     * Mark a commitment as completed.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<CommitmentResponse> completeCommitment(
            @AuthActor ActorContext actor,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        
        String note = body != null ? body.get("note") : null;
        QualityTimeCommitment commitment = commitmentService.completeCommitment(id, note);
        
        return ResponseEntity.ok(toResponse(commitment));
    }

    /**
     * Cancel a commitment.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelCommitment(
            @AuthActor ActorContext actor,
            @PathVariable Long id) {
        
        commitmentService.cancelCommitment(id);
        return ResponseEntity.noContent().build();
    }

    private Long extractFatherId(ActorContext actor) {
        UUID actorId = actor.getActorId();
        // UUID is created as new UUID(0L, domainId), so getLeastSignificantBits() returns the domain ID
        return actorId.getLeastSignificantBits();
    }

    private CommitmentResponse toResponse(QualityTimeCommitment c) {
        String childName = null;
        if (c.getChildId() != null) {
            childName = childRepository.findById(c.getChildId())
                    .map(Child::getName)
                    .orElse(null);
        }
        
        return new CommitmentResponse(
                c.getId(),
                c.getChildId(),
                childName,
                c.getScheduledAt(),
                c.getScheduledDate().toString(),
                c.getScheduledTime().toString(),
                c.getDurationMinutes(),
                c.getActivityType(),
                c.getActivityNote(),
                c.getStatus().name(),
                c.getCompletedAt(),
                c.getPointsAwarded(),
                c.getCreatedAt()
        );
    }

    // ─── DTOs ────────────────────────────────────────────────────────────

    public record CommitmentResponse(
            Long id,
            Long childId,
            String childName,
            Instant scheduledAt,
            String scheduledDate,
            String scheduledTime,
            Integer durationMinutes,
            String activityType,
            String activityNote,
            String status,
            Instant completedAt,
            Integer pointsAwarded,
            Instant createdAt
    ) {}

    public record CreateCommitmentRequest(
            Long childId,
            Instant scheduledAt,
            String activityType,
            String activityNote
    ) {}

    public record StatsResponse(
            long completed,
            long upcoming,
            long missed,
            long total,
            double completionRate
    ) {}
}
