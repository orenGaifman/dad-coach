package com.dadcoach.api.goal;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.api.error.LimitExceededException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.domain.goal.Goal;
import com.dadcoach.domain.goal.GoalRepository;
import com.dadcoach.domain.goal.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Father API controller for Goal CRUD operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/v1/fathers/me/goals — Create a new goal (max 5 active enforced)</li>
 *   <li>GET /api/v1/fathers/me/goals — List goals (cursor-based pagination)</li>
 *   <li>GET /api/v1/fathers/me/goals/{goalId} — Get a single goal</li>
 *   <li>PUT /api/v1/fathers/me/goals/{goalId} — Update goal description/priority</li>
 *   <li>POST /api/v1/fathers/me/goals/{goalId}/complete — Mark goal as completed</li>
 * </ul>
 *
 * <p>Business rules:
 * <ul>
 *   <li>Maximum of 5 active goals per father (throws LimitExceededException)</li>
 *   <li>Ownership enforced on all endpoints — Father actors see 404 for others' goals</li>
 *   <li>Completed goals cannot be modified</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/fathers/me/goals")
public class GoalController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final GoalService goalService;
    private final GoalRepository goalRepository;

    public GoalController(GoalService goalService, GoalRepository goalRepository) {
        this.goalService = goalService;
        this.goalRepository = goalRepository;
    }

    // ─── Create Goal ─────────────────────────────────────────────────────

    /**
     * Creates a new parenting goal for the authenticated father.
     * Enforces maximum 5 active goals per father.
     *
     * @param request the validated goal creation request
     * @param actor   the authenticated actor context
     * @return the created goal (201 Created)
     * @throws LimitExceededException if the father already has 5 active goals
     */
    @PostMapping
    public ResponseEntity<GoalResponseDto> createGoal(
            @Valid @RequestBody GoalCreateRequest request,
            @AuthActor ActorContext actor) {

        Long fatherId = resolveFatherId(actor);

        try {
            Goal goal = goalService.createGoal(
                    fatherId,
                    request.getTitle(),
                    request.getDescription(),
                    request.getCategory(),
                    request.getPriority()
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(GoalResponseDto.fromEntity(goal));
        } catch (BusinessRuleViolationException ex) {
            if ("MAX_GOALS_EXCEEDED".equals(ex.getRuleName())) {
                long currentCount = goalRepository.countActiveByFatherId(fatherId);
                throw new LimitExceededException("active goals", (int) currentCount, 5);
            }
            throw ex;
        }
    }

    // ─── List Goals ──────────────────────────────────────────────────────

    /**
     * Lists goals for the authenticated father with cursor-based pagination.
     * Default sort: priority ascending, then created_at descending.
     *
     * @param cursor optional cursor token for next page
     * @param limit  page size (default 20, max 100)
     * @param status optional filter by status (ACTIVE, COMPLETED, ARCHIVED)
     * @param actor  the authenticated actor context
     * @return paginated list of goals
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listGoals(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @AuthActor ActorContext actor) {

        Long fatherId = resolveFatherId(actor);
        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);

        List<Goal> goals;
        if (status != null && !status.isBlank()) {
            goals = goalRepository.findByFatherIdAndStatus(fatherId, status.toUpperCase());
        } else {
            goals = goalRepository.findByFatherId(fatherId);
        }

        // Apply cursor-based pagination (simple offset-based for now, refined in Task 11)
        int startIndex = 0;
        if (cursor != null && !cursor.isBlank()) {
            startIndex = decodeCursor(cursor);
        }

        int endIndex = Math.min(startIndex + pageSize, goals.size());
        List<Goal> page = goals.subList(startIndex, endIndex);
        boolean hasMore = endIndex < goals.size();

        List<GoalResponseDto> items = page.stream()
                .map(GoalResponseDto::fromEntity)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("next_cursor", hasMore ? encodeCursor(endIndex) : null);
        response.put("has_more", hasMore);

        return ResponseEntity.ok(response);
    }

    // ─── Get Goal ────────────────────────────────────────────────────────

    /**
     * Retrieves a single goal by ID.
     * Enforces ownership — returns 404 if the goal belongs to another father.
     *
     * @param goalId the goal ID
     * @param actor  the authenticated actor context
     * @return the goal details
     * @throws ResourceNotFoundException if goal not found or ownership mismatch
     */
    @GetMapping("/{goalId}")
    public ResponseEntity<GoalResponseDto> getGoal(
            @PathVariable Long goalId,
            @AuthActor ActorContext actor) {

        Long fatherId = resolveFatherId(actor);

        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));

        // Ownership check — 404 for mismatch (never 403)
        if (!goal.getFatherId().equals(fatherId)) {
            throw new ResourceNotFoundException("Goal", goalId);
        }

        return ResponseEntity.ok(GoalResponseDto.fromEntity(goal));
    }

    // ─── Update Goal ─────────────────────────────────────────────────────

    /**
     * Updates a goal's description and/or priority.
     * Cannot modify completed goals.
     *
     * @param goalId  the goal ID
     * @param request the update request (reuses GoalCreateRequest for validation)
     * @param actor   the authenticated actor context
     * @return the updated goal
     * @throws ResourceNotFoundException if goal not found or ownership mismatch
     */
    @PutMapping("/{goalId}")
    public ResponseEntity<GoalResponseDto> updateGoal(
            @PathVariable Long goalId,
            @Valid @RequestBody GoalUpdateRequest request,
            @AuthActor ActorContext actor) {

        Long fatherId = resolveFatherId(actor);

        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));

        // Ownership check
        if (!goal.getFatherId().equals(fatherId)) {
            throw new ResourceNotFoundException("Goal", goalId);
        }

        // Cannot modify completed goals
        if ("COMPLETED".equals(goal.getStatus())) {
            throw new com.dadcoach.api.error.OperationNotAllowedException(
                    "updateGoal", "Cannot modify a completed goal.");
        }

        // Apply updates
        if (request.getDescription() != null) {
            goal.setDescription(request.getDescription());
        }
        if (request.getPriority() != null) {
            goal.setPriority(request.getPriority());
        }
        if (request.getTitle() != null) {
            goal.setTitle(request.getTitle());
        }

        Goal saved = goalRepository.save(goal);
        return ResponseEntity.ok(GoalResponseDto.fromEntity(saved));
    }

    // ─── Complete Goal ───────────────────────────────────────────────────

    /**
     * Marks a goal as completed (state transition ACTIVE → COMPLETED).
     *
     * @param goalId the goal ID
     * @param actor  the authenticated actor context
     * @return the completed goal
     * @throws ResourceNotFoundException if goal not found or ownership mismatch
     */
    @PostMapping("/{goalId}/complete")
    public ResponseEntity<GoalResponseDto> completeGoal(
            @PathVariable Long goalId,
            @AuthActor ActorContext actor) {

        Long fatherId = resolveFatherId(actor);

        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));

        // Ownership check
        if (!goal.getFatherId().equals(fatherId)) {
            throw new ResourceNotFoundException("Goal", goalId);
        }

        Goal completed = goalService.completeGoal(goalId);
        return ResponseEntity.ok(GoalResponseDto.fromEntity(completed));
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

    /**
     * Resolves the father's internal ID from the actor context.
     * For Father actors, the actorId (UUID) needs to be mapped to the internal Long ID.
     * For now, we parse the UUID's least significant bits as the father ID.
     * This mapping will be refined when the identity provider integration is complete.
     */
    private Long resolveFatherId(ActorContext actor) {
        // The actor ID is a UUID. For the domain layer, we need the Long father ID.
        // In the current system, the JWT populates the actorId with the father's internal ID
        // encoded as a UUID. We extract the least significant bits as the Long ID.
        return actor.getActorId().getLeastSignificantBits();
    }

    /**
     * Encodes an offset as a cursor token (base64).
     */
    private String encodeCursor(int offset) {
        return Base64.getEncoder().encodeToString(String.valueOf(offset).getBytes());
    }

    /**
     * Decodes a cursor token to an offset.
     */
    private int decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getDecoder().decode(cursor));
            return Integer.parseInt(decoded);
        } catch (Exception e) {
            return 0;
        }
    }
}
