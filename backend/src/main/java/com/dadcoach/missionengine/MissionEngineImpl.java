package com.dadcoach.missionengine;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionRepository;
import com.dadcoach.father.CoachingPhase;
import com.dadcoach.mission.MissionStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Implementation of the MissionEngine responsible for difficulty adaptation,
 * equitable distribution, category non-repetition, and child selection.
 *
 * <p>Business rules implemented:
 * <ul>
 *   <li>Difficulty bounds per coaching phase (Req 6.3-6.6)</li>
 *   <li>Difficulty adaptation based on outcome ratings (Req 6.16-6.17)</li>
 *   <li>Difficulty reduction after 3 consecutive skips/expired (Req 6.11)</li>
 *   <li>Category non-repetition: max 2 per category per 7-day window per child (Req 6.7)</li>
 *   <li>Equitable distribution across children (Req 6.13)</li>
 *   <li>Child selection: least missions in 7 days, tiebreaker longest since last (Req 10.8)</li>
 * </ul>
 */
@Service
@Transactional
public class MissionEngineImpl implements MissionEngine {

    private final MissionRepository missionRepository;
    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;

    public MissionEngineImpl(MissionRepository missionRepository,
                             FatherRepository fatherRepository,
                             ChildRepository childRepository) {
        this.missionRepository = missionRepository;
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
    }

    // ─── Difficulty Bounds ────────────────────────────────────────────────

    /**
     * Returns the difficulty bounds [min, max] for a given coaching phase.
     *
     * <ul>
     *   <li>FOUNDATION: [1, 2]</li>
     *   <li>BUILDING: [1, 3]</li>
     *   <li>DEEPENING: [2, 4]</li>
     *   <li>MASTERY: [2, 5]</li>
     * </ul>
     *
     * @param phase the current coaching phase
     * @return an int array of [min, max]
     */
    public int[] getDifficultyBounds(CoachingPhase phase) {
        return switch (phase) {
            case FOUNDATION -> new int[]{1, 2};
            case BUILDING -> new int[]{1, 3};
            case DEEPENING -> new int[]{2, 4};
            case MASTERY -> new int[]{2, 5};
        };
    }

    /**
     * Clamps a difficulty value to the bounds defined by the coaching phase.
     *
     * @param difficulty the difficulty to clamp
     * @param phase      the coaching phase defining bounds
     * @return the clamped difficulty
     */
    public int clampDifficulty(int difficulty, CoachingPhase phase) {
        int[] bounds = getDifficultyBounds(phase);
        return Math.max(bounds[0], Math.min(difficulty, bounds[1]));
    }

    // ─── Mission Generation ──────────────────────────────────────────────

    @Override
    public Mission generateMission(Long fatherId, Long childId) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new ResourceNotFoundException("Child", childId));

        // Enforce single-active-mission-per-child constraint
        long activeCount = missionRepository.countActiveMissionsByChildId(childId);
        if (activeCount > 0) {
            throw new BusinessRuleViolationException("SINGLE_ACTIVE_MISSION_PER_CHILD",
                    "Child " + childId + " already has an active mission");
        }

        // Determine difficulty based on adaptation logic
        CoachingPhase phase = father.getCoachingPhase();
        int[] bounds = getDifficultyBounds(phase);
        int difficulty = adaptDifficulty(fatherId, childId, bounds[0]);

        // Select a valid category (non-repetition)
        String category = selectCategory(childId);

        // Create placeholder mission — AI-generated content will be integrated later
        Mission mission = new Mission(father, child, "Generated Mission", "AI-generated mission content",
                category, difficulty, 20);

        return missionRepository.save(mission);
    }

    // ─── Difficulty Adaptation ───────────────────────────────────────────

    @Override
    public int adaptDifficulty(Long fatherId, Long childId, int currentDifficulty) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));
        CoachingPhase phase = father.getCoachingPhase();
        int[] bounds = getDifficultyBounds(phase);

        // Check for 3 consecutive skipped/expired missions first (Req 6.11)
        if (hasThreeConsecutiveSkipsOrExpired(childId)) {
            int adapted = Math.max(1, currentDifficulty - 1);
            return Math.max(bounds[0], Math.min(adapted, bounds[1]));
        }

        // Check most recent completed mission outcome rating (Req 6.16-6.17)
        List<Mission> recentCompleted = missionRepository.findRecentCompletedByChildId(childId, 1);
        if (!recentCompleted.isEmpty()) {
            Mission lastCompleted = recentCompleted.get(0);
            Integer rating = lastCompleted.getOutcomeRating();
            if (rating != null) {
                if (rating >= 4) {
                    // Rating 4-5: increase difficulty by 1, capped at phase max
                    int adapted = Math.min(currentDifficulty + 1, bounds[1]);
                    return Math.max(bounds[0], adapted);
                } else if (rating <= 2) {
                    // Rating 1-2: decrease difficulty by 1, minimum 1
                    int adapted = Math.max(currentDifficulty - 1, 1);
                    return Math.max(bounds[0], Math.min(adapted, bounds[1]));
                }
            }
        }

        // Rating 3 or no recent mission: unchanged, but clamped to phase bounds
        return Math.max(bounds[0], Math.min(currentDifficulty, bounds[1]));
    }

    // ─── Category Non-Repetition ─────────────────────────────────────────

    /**
     * Validates whether a given category can be assigned to a child without
     * violating the non-repetition constraint (max 2 per category per 7-day window).
     *
     * @param childId  the child ID
     * @param category the proposed category
     * @return true if the category is allowed (count < 2 in last 7 days)
     */
    public boolean validateCategoryNonRepetition(Long childId, String category) {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long count = missionRepository.countByChildIdAndCategorySince(childId, category, sevenDaysAgo);
        return count < 2;
    }

    // ─── Equitable Distribution ──────────────────────────────────────────

    @Override
    public boolean isDistributionEquitable(Long fatherId, int windowDays) {
        List<Child> activeChildren = getActiveChildren(fatherId);
        if (activeChildren.size() <= 1) {
            return true; // Single child or no children — always equitable
        }

        Instant windowStart = Instant.now().minus(windowDays, ChronoUnit.DAYS);

        // Count missions per child in the window
        long totalMissions = 0;
        long minMissions = Long.MAX_VALUE;

        for (Child child : activeChildren) {
            long count = missionRepository.countMissionsByChildIdSince(child.getId(), windowStart);
            totalMissions += count;
            if (count < minMissions) {
                minMissions = count;
            }
        }

        // Equitable: each child gets at least floor(total/N) - 1
        long numChildren = activeChildren.size();
        long threshold = (totalMissions / numChildren) - 1;

        // If threshold is negative (< 0 total missions), distribution is trivially equitable
        if (threshold < 0) {
            return true;
        }

        return minMissions >= threshold;
    }

    // ─── Child Selection Algorithm ───────────────────────────────────────

    @Override
    public Long selectNextChild(Long fatherId) {
        List<Child> activeChildren = getActiveChildren(fatherId);
        if (activeChildren.isEmpty()) {
            throw new BusinessRuleViolationException("NO_ACTIVE_CHILDREN",
                    "Father " + fatherId + " has no active children");
        }

        if (activeChildren.size() == 1) {
            return activeChildren.get(0).getId();
        }

        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        // For each child, compute: missions in last 7 days + last mission time
        record ChildMissionStats(Long childId, long missionsInWindow, Instant lastMissionAt) {}

        List<ChildMissionStats> stats = activeChildren.stream()
                .map(child -> {
                    long missionCount = missionRepository.countMissionsByChildIdSince(child.getId(), sevenDaysAgo);
                    Instant lastMission = getLastMissionTimeForChild(child.getId());
                    return new ChildMissionStats(child.getId(), missionCount,
                            lastMission != null ? lastMission : Instant.EPOCH);
                })
                .toList();

        // Sort: least missions first, then longest since last mission (earliest lastMissionAt first)
        return stats.stream()
                .min(Comparator.comparingLong(ChildMissionStats::missionsInWindow)
                        .thenComparing(ChildMissionStats::lastMissionAt))
                .map(ChildMissionStats::childId)
                .orElseThrow(() -> new BusinessRuleViolationException("NO_ACTIVE_CHILDREN",
                        "Father " + fatherId + " has no active children"));
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

    /**
     * Checks whether the child has 3 consecutive skipped or expired missions
     * (most recent missions in the last 30 days).
     */
    private boolean hasThreeConsecutiveSkipsOrExpired(Long childId) {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        // Get all recent missions for this child (ordered by assignedAt DESC)
        List<Mission> allRecent = missionRepository.findRecentByChildIdSince(childId, thirtyDaysAgo);
        if (allRecent.size() < 3) {
            // Check if all missions in the window are skipped/expired
            return allRecent.size() >= 3 && allRecent.stream()
                    .limit(3)
                    .allMatch(m -> m.getStatus() == MissionStatus.SKIPPED || m.getStatus() == MissionStatus.EXPIRED);
        }

        // Check the 3 most recent missions (by assignedAt desc)
        int consecutiveSkips = 0;
        for (Mission m : allRecent) {
            if (m.getStatus() == MissionStatus.SKIPPED || m.getStatus() == MissionStatus.EXPIRED) {
                consecutiveSkips++;
                if (consecutiveSkips >= 3) {
                    return true;
                }
            } else {
                break; // Non-skipped/expired mission breaks the streak
            }
        }
        return false;
    }

    /**
     * Select a category for the next mission, avoiding categories that would violate
     * the non-repetition constraint.
     */
    private String selectCategory(Long childId) {
        // Default categories for mission generation
        String[] categories = {"CONNECTION", "COMMUNICATION", "DISCIPLINE", "EDUCATION",
                "HEALTH", "EMOTIONAL", "INDEPENDENCE", "FUN", "ROUTINE"};

        for (String category : categories) {
            if (validateCategoryNonRepetition(childId, category)) {
                return category;
            }
        }

        // All categories are at max — return the first one (fallback, should rarely happen with 9 categories)
        return categories[0];
    }

    /**
     * Gets active children for a father.
     */
    private List<Child> getActiveChildren(Long fatherId) {
        return childRepository.findByFatherId(fatherId).stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .toList();
    }

    /**
     * Gets the last mission assignment time for a child.
     * Returns null if the child has never received a mission.
     */
    private Instant getLastMissionTimeForChild(Long childId) {
        List<Mission> missions = missionRepository.findMostRecentByChildId(childId);
        if (missions.isEmpty()) {
            return null;
        }
        return missions.get(0).getAssignedAt();
    }
}
