package com.dadcoach.api.mission;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.api.error.ResourceNotFoundException;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Father API controller for read-only Mission access.
 *
 * <p>Missions are created exclusively by the coaching orchestration pipeline (SPEC-005).
 * The Father API provides read-only access for viewing mission history and active missions.</p>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/v1/fathers/me/missions — List missions (cursor-based pagination)</li>
 *   <li>GET /api/v1/fathers/me/missions/active — Get the current active mission</li>
 *   <li>GET /api/v1/fathers/me/missions/{missionId} — Get a single mission</li>
 * </ul>
 *
 * <p>Ownership enforced on all endpoints — Father actors see 404 for others' missions.</p>
 */
@RestController
@RequestMapping("/api/v1/fathers/me/missions")
public class MissionController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final MissionRepository missionRepository;

    public MissionController(MissionRepository missionRepository) {
        this.missionRepository = missionRepository;
    }

    // ─── List Missions ───────────────────────────────────────────────────

    /**
     * Lists missions for the authenticated father with cursor-based pagination.
     * Default sort: assigned_at descending (most recent first).
     * Filterable by status.
     *
     * @param cursor optional cursor token for next page
     * @param limit  page size (default 20, max 100)
     * @param status optional filter by mission status
     * @param actor  the authenticated actor context
     * @return paginated list of missions
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listMissions(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @AuthActor ActorContext actor) {

        Long fatherId = resolveFatherId(actor);
        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);

        // Retrieve all missions for the father, ordered by assigned_at descending
        List<Mission> missions = missionRepository.findByFatherIdOrderByAssignedAtDesc(fatherId);

        // Apply status filter if provided
        if (status != null && !status.isBlank()) {
            String upperStatus = status.toUpperCase();
            missions = missions.stream()
                    .filter(m -> m.getStatus().name().equals(upperStatus))
                    .toList();
        }

        // Apply cursor-based pagination (simple offset-based for now, refined in Task 11)
        int startIndex = 0;
        if (cursor != null && !cursor.isBlank()) {
            startIndex = decodeCursor(cursor);
        }

        int endIndex = Math.min(startIndex + pageSize, missions.size());
        List<Mission> page = (startIndex < missions.size())
                ? missions.subList(startIndex, endIndex)
                : List.of();
        boolean hasMore = endIndex < missions.size();

        List<MissionResponseDto> items = page.stream()
                .map(MissionResponseDto::fromEntity)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("next_cursor", hasMore ? encodeCursor(endIndex) : null);
        response.put("has_more", hasMore);

        return ResponseEntity.ok(response);
    }

    // ─── Get Active Mission ──────────────────────────────────────────────

    /**
     * Returns the current active mission for the authenticated father, if any.
     * Active states: ASSIGNED, ACCEPTED, IN_PROGRESS.
     *
     * @param actor the authenticated actor context
     * @return the active mission, or null body if none active
     */
    @GetMapping("/active")
    public ResponseEntity<MissionResponseDto> getActiveMission(@AuthActor ActorContext actor) {

        Long fatherId = resolveFatherId(actor);

        // Find active missions for the father
        List<Mission> allMissions = missionRepository.findByFatherIdOrderByAssignedAtDesc(fatherId);
        Mission activeMission = allMissions.stream()
                .filter(Mission::isActive)
                .findFirst()
                .orElse(null);

        if (activeMission == null) {
            return ResponseEntity.ok(null);
        }

        return ResponseEntity.ok(MissionResponseDto.fromEntity(activeMission));
    }

    // ─── Get Mission ─────────────────────────────────────────────────────

    /**
     * Retrieves a single mission by ID.
     * Enforces ownership — returns 404 if the mission belongs to another father.
     *
     * @param missionId the mission ID
     * @param actor     the authenticated actor context
     * @return the mission details
     * @throws ResourceNotFoundException if mission not found or ownership mismatch
     */
    @GetMapping("/{missionId}")
    public ResponseEntity<MissionResponseDto> getMission(
            @PathVariable Long missionId,
            @AuthActor ActorContext actor) {

        Long fatherId = resolveFatherId(actor);

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", missionId));

        // Ownership check — 404 for mismatch (never 403)
        if (!mission.getFatherId().equals(fatherId)) {
            throw new ResourceNotFoundException("Mission", missionId);
        }

        return ResponseEntity.ok(MissionResponseDto.fromEntity(mission));
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

    /**
     * Resolves the father's internal ID from the actor context.
     * For Father actors, the actorId (UUID) is mapped to the internal Long ID.
     */
    private Long resolveFatherId(ActorContext actor) {
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
