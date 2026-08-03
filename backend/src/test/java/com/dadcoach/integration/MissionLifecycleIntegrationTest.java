package com.dadcoach.integration;

import com.dadcoach.IntegrationTestBase;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildService;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherService;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionService;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.mission.LegacyMissionStatus;
import static com.dadcoach.mission.LegacyMissionStatus.*;
import com.dadcoach.missionengine.MissionEngineImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: mission lifecycle.
 * Generate → accept → complete → difficulty adaptation.
 *
 * Verifies the mission state machine and difficulty adaptation work end-to-end
 * with a real database.
 */
@Transactional
class MissionLifecycleIntegrationTest extends IntegrationTestBase {

    @Autowired
    private FatherService fatherService;

    @Autowired
    private ChildService childService;

    @Autowired
    private MissionService missionService;

    @Autowired
    private MissionEngineImpl missionEngine;

    private Father father;
    private Child child;

    @BeforeEach
    void setUp() {
        // Create an active father with a child
        father = fatherService.createFather("+972509876543");
        fatherService.transitionStatus(father.getId(), FatherStatus.ONBOARDING, "Onboarding");
        father = fatherService.activateFather(father.getId());
        child = childService.createChild(father.getId(), "Noam", LocalDate.now().minusYears(7));
    }

    @Test
    void missionLifecycle_assignAcceptCompleteWithDifficultyAdaptation() {
        // Step 1: Create a mission
        Mission mission = missionService.createMission(
                father.getId(), child.getId(), null,
                "Build a LEGO tower together", "Build a tall LEGO tower with your child",
                "CONNECTION", 2, 20
        );
        assertThat(mission.getId()).isNotNull();
        assertThat(mission.getStatus()).isEqualTo(ASSIGNED);

        // Step 2: Accept mission
        Mission accepted = missionService.acceptMission(mission.getId());
        assertThat(accepted.getStatus()).isEqualTo(ACCEPTED);

        // Step 3: Start mission
        Mission inProgress = missionService.startMission(mission.getId());
        assertThat(inProgress.getStatus()).isEqualTo(IN_PROGRESS);

        // Step 4: Complete with a high rating
        Mission completed = missionService.completeMission(mission.getId(), 5, "Great bonding time!");
        assertThat(completed.getStatus()).isEqualTo(COMPLETED);
        assertThat(completed.getOutcomeRating()).isEqualTo(5);

        // Step 5: Verify difficulty adaptation (rating 5 → increase difficulty)
        int adapted = missionEngine.adaptDifficulty(father.getId(), child.getId(), 2);
        // For FOUNDATION phase (days 0-14), bounds are [1,2], so adapted should be capped at 2
        assertThat(adapted).isGreaterThanOrEqualTo(1);
        assertThat(adapted).isLessThanOrEqualTo(2); // Capped by FOUNDATION phase max
    }

    @Test
    void missionLifecycle_skipFlow() {
        Mission mission = missionService.createMission(
                father.getId(), child.getId(), null,
                "Read a bedtime story", "Read a story together before bed",
                "COMMUNICATION", 1, 15
        );

        // Skip the mission
        Mission skipped = missionService.skipMission(mission.getId());
        assertThat(skipped.getStatus()).isEqualTo(SKIPPED);
    }
}
