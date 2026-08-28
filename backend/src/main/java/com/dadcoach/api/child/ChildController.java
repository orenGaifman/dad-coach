package com.dadcoach.api.child;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.api.auth.RolePermission;
import com.dadcoach.api.error.LimitExceededException;
import com.dadcoach.common.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Child CRUD operations under the Father API surface.
 * <p>
 * All endpoints enforce resource ownership: a father can only access their own children.
 * Ownership mismatch returns 404 (not 403) to prevent resource enumeration
 * (per SPEC-007 Requirement 6 criteria 3).
 * <p>
 * Business rules enforced:
 * <ul>
 *   <li>Maximum 8 children per father (SPEC-002 Req 2 criteria 2)</li>
 *   <li>Birth date must be 0-18 years in the past (SPEC-002 Req 2 criteria 4)</li>
 *   <li>Child age computed dynamically from birth_date (SPEC-002 Req 2 criteria 3)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/fathers/me/children")
public class ChildController {

    private static final int MAX_CHILDREN_PER_FATHER = 8;

    private final ChildApiService childService;

    public ChildController(ChildApiService childService) {
        this.childService = childService;
    }

    /**
     * Creates a new child for the authenticated father.
     * <p>
     * Enforces the maximum of 8 children per father business rule.
     * Validates birth date is within the 0-18 year range.
     *
     * @param request the validated child creation request
     * @param actor   the authenticated actor context
     * @return the created child with 201 Created status
     * @throws LimitExceededException if father already has 8 children
     */
    @PostMapping
    public ResponseEntity<ChildResponseDto> createChild(
            @Valid @RequestBody ChildCreateRequest request,
            @AuthActor ActorContext actor) {

        UUID fatherId = actor.getActorId();

        // Validate birth date is within 0-18 year range
        if (!request.isBirthDateInValidRange()) {
            throw new IllegalArgumentException("Birth date must be between 0 and 18 years in the past");
        }

        // Enforce max 8 children business rule
        int currentCount = childService.countActiveChildren(fatherId);
        if (currentCount >= MAX_CHILDREN_PER_FATHER) {
            throw new LimitExceededException("children per father", currentCount, MAX_CHILDREN_PER_FATHER);
        }

        ChildResponseDto created = childService.createChild(fatherId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists all active children for the authenticated father.
     *
     * @param actor the authenticated actor context
     * @return list of child response DTOs
     */
    @GetMapping
    public ResponseEntity<List<ChildResponseDto>> listChildren(@AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        List<ChildResponseDto> children = childService.listChildren(fatherId);
        return ResponseEntity.ok(children);
    }

    /**
     * Gets a single child by ID with ownership verification.
     * <p>
     * Returns 404 (not 403) if the child belongs to another father,
     * preventing resource enumeration.
     *
     * @param childId the child's unique identifier
     * @param actor   the authenticated actor context
     * @return the child response DTO
     * @throws ResourceNotFoundException if child not found or ownership mismatch
     */
    @GetMapping("/{childId}")
    public ResponseEntity<ChildResponseDto> getChild(
            @PathVariable UUID childId,
            @AuthActor ActorContext actor) {

        ChildResponseDto child = childService.findById(childId)
                .orElseThrow(() -> new ResourceNotFoundException("Child", childId));

        // Ownership check: 404 for mismatch (not 403)
        RolePermission.assertOwnership(actor, child.getFatherId(), "Child", childId);

        return ResponseEntity.ok(child);
    }

    /**
     * Updates an existing child's details.
     * <p>
     * Validates ownership before allowing the update.
     * Validates birth date range if provided.
     *
     * @param childId the child's unique identifier
     * @param request the validated update request
     * @param actor   the authenticated actor context
     * @return the updated child response DTO
     * @throws ResourceNotFoundException if child not found or ownership mismatch
     */
    @PutMapping("/{childId}")
    public ResponseEntity<ChildResponseDto> updateChild(
            @PathVariable UUID childId,
            @Valid @RequestBody ChildCreateRequest request,
            @AuthActor ActorContext actor) {

        // Verify the child exists and check ownership
        UUID ownerFatherId = childService.getOwnerFatherId(childId)
                .orElseThrow(() -> new ResourceNotFoundException("Child", childId));

        RolePermission.assertOwnership(actor, ownerFatherId, "Child", childId);

        // Validate birth date range
        if (!request.isBirthDateInValidRange()) {
            throw new IllegalArgumentException("Birth date must be between 0 and 18 years in the past");
        }

        ChildResponseDto updated = childService.updateChild(childId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Soft-deletes (archives) a child.
     * <p>
     * The child is transitioned to ARCHIVED status, excluding them from
     * active coaching and mission generation. This operation is reversible.
     *
     * @param childId the child's unique identifier
     * @param actor   the authenticated actor context
     * @return 204 No Content on success
     * @throws ResourceNotFoundException if child not found or ownership mismatch
     */
    @DeleteMapping("/{childId}")
    public ResponseEntity<Void> deleteChild(
            @PathVariable UUID childId,
            @AuthActor ActorContext actor) {

        // Verify the child exists and check ownership
        UUID ownerFatherId = childService.getOwnerFatherId(childId)
                .orElseThrow(() -> new ResourceNotFoundException("Child", childId));

        RolePermission.assertOwnership(actor, ownerFatherId, "Child", childId);

        childService.deleteChild(childId);
        return ResponseEntity.noContent().build();
    }
}
