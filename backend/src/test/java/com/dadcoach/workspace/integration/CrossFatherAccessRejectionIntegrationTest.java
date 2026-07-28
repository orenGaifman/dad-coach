package com.dadcoach.workspace.integration;

import com.dadcoach.workspace.ResourceNotFoundException;
import com.dadcoach.workspace.aggregation.*;
import com.dadcoach.workspace.security.WorkspaceOwnershipEnforcer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Integration test for cross-father access rejection.
 *
 * <p>Verifies that accessing resources belonging to a different father
 * results in ResourceNotFoundException (404), not 403, to prevent enumeration.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("15.4 - Cross-Father Access Rejection Integration")
class CrossFatherAccessRejectionIntegrationTest {

    @Mock
    private ChildDataService childDataService;

    @Mock
    private MissionDataService missionDataService;

    @Mock
    private GoalDataService goalDataService;

    private WorkspaceOwnershipEnforcer ownershipEnforcer;
    private ChildrenOverviewService childrenOverviewService;

    @BeforeEach
    void setUp() {
        ownershipEnforcer = new WorkspaceOwnershipEnforcer(childDataService);
        childrenOverviewService = new ChildrenOverviewService(
                childDataService, missionDataService, goalDataService, Clock.systemUTC());
    }

    @Test
    @DisplayName("verifyFatherOwnership with different IDs → ResourceNotFoundException (404)")
    void verifyFatherOwnership_differentIds_throwsResourceNotFoundException() {
        // Given
        UUID authenticatedFatherId = UUID.randomUUID();
        UUID targetFatherId = UUID.randomUUID();

        // When/Then
        assertThatThrownBy(() -> ownershipEnforcer.verifyFatherOwnership(authenticatedFatherId, targetFatherId))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> {
                    ResourceNotFoundException rnf = (ResourceNotFoundException) ex;
                    assertThat(rnf.getEntityType()).isEqualTo("father");
                    assertThat(rnf.getIdentifier()).isEqualTo(targetFatherId);
                });
    }

    @Test
    @DisplayName("verifyFatherOwnership with same IDs → no exception")
    void verifyFatherOwnership_sameIds_noException() {
        // Given
        UUID fatherId = UUID.randomUUID();

        // When/Then - no exception
        ownershipEnforcer.verifyFatherOwnership(fatherId, fatherId);
    }

    @Test
    @DisplayName("verifyChildBelongsToFather with wrong father → ResourceNotFoundException (404)")
    void verifyChildBelongsToFather_wrongFather_throwsResourceNotFoundException() {
        // Given
        UUID fatherId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        // Child does NOT belong to this father
        when(childDataService.childBelongsToFather(fatherId, childId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> ownershipEnforcer.verifyChildBelongsToFather(fatherId, childId))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> {
                    ResourceNotFoundException rnf = (ResourceNotFoundException) ex;
                    assertThat(rnf.getEntityType()).isEqualTo("child");
                    assertThat(rnf.getIdentifier()).isEqualTo(childId);
                });
    }

    @Test
    @DisplayName("verifyChildBelongsToFather with correct father → no exception")
    void verifyChildBelongsToFather_correctFather_noException() {
        // Given
        UUID fatherId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        when(childDataService.childBelongsToFather(fatherId, childId)).thenReturn(true);

        // When/Then - no exception
        ownershipEnforcer.verifyChildBelongsToFather(fatherId, childId);
    }

    @Test
    @DisplayName("ChildrenOverviewService.getChildSummary with wrong father → 404")
    void getChildSummary_wrongFather_throwsResourceNotFoundException() {
        // Given
        UUID fatherId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        // Child does NOT belong to this father
        when(childDataService.childBelongsToFather(fatherId, childId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> childrenOverviewService.getChildSummary(fatherId, childId))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> {
                    ResourceNotFoundException rnf = (ResourceNotFoundException) ex;
                    assertThat(rnf.getEntityType()).isEqualTo("child");
                    assertThat(rnf.getIdentifier()).isEqualTo(childId);
                });
    }
}
