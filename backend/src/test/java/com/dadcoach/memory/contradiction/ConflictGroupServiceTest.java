package com.dadcoach.memory.contradiction;

import com.dadcoach.memory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConflictGroupService}.
 *
 * <p>Validates: SPEC-004 Requirement 7 (Memory Conflicts and Contradiction Resolution)
 * <ul>
 *   <li>Criteria 4: Groups conflicting memories under a shared conflict_group_id</li>
 *   <li>Criteria 5: Resolves conflict_groups based on access patterns</li>
 *   <li>Criteria 8: Tracks conflict resolution history</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConflictGroupService Tests")
class ConflictGroupServiceTest {

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private ContradictionDetectionService contradictionDetectionService;

    private ConflictGroupService service;

    private static final UUID FATHER_ID = UUID.randomUUID();
    private static final UUID CHILD_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ConflictGroupService(memoryRepository, contradictionDetectionService);
    }

    // ─── Helper Methods ──────────────────────────────────────────────────

    private Memory createMemory(String content, MemoryCategory category, BigDecimal confidence) {
        return createMemory(content, category, confidence, MemoryState.ACTIVE);
    }

    private Memory createMemory(String content, MemoryCategory category, BigDecimal confidence, MemoryState state) {
        Memory memory = new Memory(
                FATHER_ID,
                category,
                MemorySubjectType.CHILD,
                content,
                5,
                confidence,
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(UUID.randomUUID());
        memory.setChildId(CHILD_ID);
        memory.setState(state);
        return memory;
    }

    private Contradiction createContradiction(Memory existing, Memory newMemory) {
        return new Contradiction(
                existing,
                newMemory,
                0.8,
                ContradictionType.NEGATION,
                "Test contradiction"
        );
    }

    // ─── Conflict Group Creation Tests ───────────────────────────────────

    @Nested
    @DisplayName("Conflict Group Creation")
    class ConflictGroupCreationTests {

        @Test
        @DisplayName("Creates new conflict group for two eligible memories")
        void createsNewConflictGroupForEligibleMemories() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.7"));
            Contradiction contradiction = createContradiction(existing, newMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            UUID groupId = service.groupConflictingMemories(contradiction);

            assertThat(groupId).isNotNull();
            
            // Verify both memories were saved with the conflict group ID
            ArgumentCaptor<Memory> memoryCaptor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository, times(2)).save(memoryCaptor.capture());
            
            List<Memory> savedMemories = memoryCaptor.getAllValues();
            assertThat(savedMemories).allMatch(m -> m.getConflictGroupId() != null);
            assertThat(savedMemories).allMatch(m -> m.getConflictGroupId().equals(groupId));
        }

        @Test
        @DisplayName("Returns null when existing memory has low confidence")
        void returnsNullWhenExistingMemoryHasLowConfidence() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.4"));
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            Contradiction contradiction = createContradiction(existing, newMemory);

            UUID groupId = service.groupConflictingMemories(contradiction);

            assertThat(groupId).isNull();
            verify(memoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Returns null when new memory has low confidence")
        void returnsNullWhenNewMemoryHasLowConfidence() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.3"));
            Contradiction contradiction = createContradiction(existing, newMemory);

            UUID groupId = service.groupConflictingMemories(contradiction);

            assertThat(groupId).isNull();
            verify(memoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Returns null when memory is not in ACTIVE/CONFIRMED state")
        void returnsNullWhenMemoryNotActive() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.8"), MemoryState.SUPERSEDED);
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.8"));
            Contradiction contradiction = createContradiction(existing, newMemory);

            UUID groupId = service.groupConflictingMemories(contradiction);

            assertThat(groupId).isNull();
        }

        @Test
        @DisplayName("Uses existing conflict group from first memory")
        void usesExistingConflictGroupFromFirstMemory() {
            UUID existingGroupId = UUID.randomUUID();
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            existing.setConflictGroupId(existingGroupId);
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.7"));
            Contradiction contradiction = createContradiction(existing, newMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            UUID groupId = service.groupConflictingMemories(contradiction);

            assertThat(groupId).isEqualTo(existingGroupId);
        }

        @Test
        @DisplayName("Uses existing conflict group from second memory")
        void usesExistingConflictGroupFromSecondMemory() {
            UUID existingGroupId = UUID.randomUUID();
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.7"));
            newMemory.setConflictGroupId(existingGroupId);
            Contradiction contradiction = createContradiction(existing, newMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            UUID groupId = service.groupConflictingMemories(contradiction);

            assertThat(groupId).isEqualTo(existingGroupId);
        }

        @Test
        @DisplayName("Merges conflict groups when both memories have different groups")
        void mergesConflictGroupsWhenDifferent() {
            UUID group1 = UUID.randomUUID();
            UUID group2 = UUID.randomUUID();
            
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            existing.setConflictGroupId(group1);
            
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.7"));
            newMemory.setConflictGroupId(group2);
            
            Memory otherInGroup2 = createMemory("Another memory", MemoryCategory.PREFERENCE, new BigDecimal("0.6"));
            otherInGroup2.setConflictGroupId(group2);
            
            Contradiction contradiction = createContradiction(existing, newMemory);

            when(memoryRepository.findByConflictGroupId(group2)).thenReturn(List.of(newMemory, otherInGroup2));
            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            UUID groupId = service.groupConflictingMemories(contradiction);

            // Should use group1 (the first memory's group)
            assertThat(groupId).isEqualTo(group1);
            
            // Should have merged memories from group2 into group1
            verify(memoryRepository).findByConflictGroupId(group2);
        }

        @Test
        @DisplayName("Throws exception for null contradiction")
        void throwsExceptionForNullContradiction() {
            assertThatThrownBy(() -> service.groupConflictingMemories((Contradiction) null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contradiction cannot be null");
        }

        @Test
        @DisplayName("Groups with confidence exactly at threshold (0.5)")
        void groupsWithConfidenceAtThreshold() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.50"));
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.50"));
            Contradiction contradiction = createContradiction(existing, newMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            UUID groupId = service.groupConflictingMemories(contradiction);

            assertThat(groupId).isNotNull();
        }

        @Test
        @DisplayName("Groups memories in CONFIRMED state")
        void groupsMemoriesInConfirmedState() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.9"), MemoryState.CONFIRMED);
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.8"));
            Contradiction contradiction = createContradiction(existing, newMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            UUID groupId = service.groupConflictingMemories(contradiction);

            assertThat(groupId).isNotNull();
        }
    }

    // ─── Conflict Group Retrieval Tests ──────────────────────────────────

    @Nested
    @DisplayName("Conflict Group Retrieval")
    class ConflictGroupRetrievalTests {

        @Test
        @DisplayName("Retrieves memories by conflict group ID")
        void retrievesMemoriesByConflictGroupId() {
            UUID groupId = UUID.randomUUID();
            Memory memory1 = createMemory("Memory 1", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            Memory memory2 = createMemory("Memory 2", MemoryCategory.PREFERENCE, new BigDecimal("0.7"));
            
            when(memoryRepository.findByConflictGroupId(groupId)).thenReturn(List.of(memory1, memory2));

            List<Memory> result = service.getConflictGroup(groupId);

            assertThat(result).hasSize(2);
            verify(memoryRepository).findByConflictGroupId(groupId);
        }

        @Test
        @DisplayName("Returns empty list for null conflict group ID")
        void returnsEmptyListForNullGroupId() {
            List<Memory> result = service.getConflictGroup(null);

            assertThat(result).isEmpty();
            verify(memoryRepository, never()).findByConflictGroupId(any());
        }

        @Test
        @DisplayName("Retrieves all conflict groups for a father")
        void retrievesAllConflictGroupsForFather() {
            UUID group1 = UUID.randomUUID();
            UUID group2 = UUID.randomUUID();
            
            Memory memory1 = createMemory("Memory 1", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            memory1.setConflictGroupId(group1);
            Memory memory2 = createMemory("Memory 2", MemoryCategory.PREFERENCE, new BigDecimal("0.7"));
            memory2.setConflictGroupId(group1);
            Memory memory3 = createMemory("Memory 3", MemoryCategory.IDENTITY, new BigDecimal("0.6"));
            memory3.setConflictGroupId(group2);
            
            Collection<MemoryState> activeStates = EnumSet.of(MemoryState.ACTIVE, MemoryState.CONFIRMED);
            when(memoryRepository.findConflictingMemories(FATHER_ID, activeStates))
                    .thenReturn(List.of(memory1, memory2, memory3));

            Map<UUID, List<Memory>> groups = service.getConflictGroupsForFather(FATHER_ID);

            assertThat(groups).hasSize(2);
            assertThat(groups.get(group1)).hasSize(2);
            assertThat(groups.get(group2)).hasSize(1);
        }

        @Test
        @DisplayName("Throws exception for null father ID")
        void throwsExceptionForNullFatherId() {
            assertThatThrownBy(() -> service.getConflictGroupsForFather(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fatherId cannot be null");
        }

        @Test
        @DisplayName("Returns correct conflict group count")
        void returnsCorrectConflictGroupCount() {
            UUID group1 = UUID.randomUUID();
            UUID group2 = UUID.randomUUID();
            
            Memory memory1 = createMemory("Memory 1", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            memory1.setConflictGroupId(group1);
            Memory memory2 = createMemory("Memory 2", MemoryCategory.IDENTITY, new BigDecimal("0.6"));
            memory2.setConflictGroupId(group2);
            
            Collection<MemoryState> activeStates = EnumSet.of(MemoryState.ACTIVE, MemoryState.CONFIRMED);
            when(memoryRepository.findConflictingMemories(FATHER_ID, activeStates))
                    .thenReturn(List.of(memory1, memory2));

            int count = service.getConflictGroupCount(FATHER_ID);

            assertThat(count).isEqualTo(2);
        }
    }

    // ─── Conflict Resolution Analysis Tests ──────────────────────────────

    @Nested
    @DisplayName("Conflict Resolution Analysis")
    class ConflictResolutionAnalysisTests {

        @Test
        @DisplayName("Returns NO_ACTION for empty conflict group")
        void returnsNoActionForEmptyGroup() {
            UUID groupId = UUID.randomUUID();
            when(memoryRepository.findByConflictGroupId(groupId)).thenReturn(Collections.emptyList());

            ConflictResolution resolution = service.analyzeConflictGroup(groupId);

            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.NO_ACTION);
            assertThat(resolution.reason()).contains("No memories");
        }

        @Test
        @DisplayName("Returns CLEAR_GROUP for single memory")
        void returnsClearGroupForSingleMemory() {
            UUID groupId = UUID.randomUUID();
            Memory memory = createMemory("Single memory", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            memory.setConflictGroupId(groupId);
            
            when(memoryRepository.findByConflictGroupId(groupId)).thenReturn(List.of(memory));

            ConflictResolution resolution = service.analyzeConflictGroup(groupId);

            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.CLEAR_GROUP);
            assertThat(resolution.winningMemory()).isEqualTo(memory);
        }

        @Test
        @DisplayName("Supersedes unaccessed memory when one is recently accessed")
        void supersedesUnaccessedWhenOneRecentlyAccessed() {
            UUID groupId = UUID.randomUUID();
            Instant now = Instant.now();
            
            Memory recentlyAccessed = createMemory("Recently accessed", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            recentlyAccessed.setConflictGroupId(groupId);
            recentlyAccessed.setLastAccessedAt(now.minusSeconds(5 * 24 * 60 * 60)); // 5 days ago
            
            Memory notRecentlyAccessed = createMemory("Not recently accessed", MemoryCategory.PREFERENCE, new BigDecimal("0.7"));
            notRecentlyAccessed.setConflictGroupId(groupId);
            notRecentlyAccessed.setLastAccessedAt(now.minusSeconds(20 * 24 * 60 * 60)); // 20 days ago
            
            when(memoryRepository.findByConflictGroupId(groupId))
                    .thenReturn(List.of(recentlyAccessed, notRecentlyAccessed));

            ConflictResolution resolution = service.analyzeConflictGroup(groupId);

            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.SUPERSEDE);
            assertThat(resolution.winningMemory()).isEqualTo(recentlyAccessed);
            assertThat(resolution.affectedMemories()).contains(notRecentlyAccessed);
        }

        @Test
        @DisplayName("Expires lowest confidence when neither accessed in 30+ days")
        void expiresLowestConfidenceWhenNeitherAccessed() {
            UUID groupId = UUID.randomUUID();
            Instant now = Instant.now();
            Instant longAgo = now.minusSeconds(35 * 24 * 60 * 60); // 35 days ago
            
            Memory higherConfidence = createMemory("Higher confidence", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            higherConfidence.setConflictGroupId(groupId);
            higherConfidence.setLastAccessedAt(longAgo);
            
            Memory lowerConfidence = createMemory("Lower confidence", MemoryCategory.PREFERENCE, new BigDecimal("0.6"));
            lowerConfidence.setConflictGroupId(groupId);
            lowerConfidence.setLastAccessedAt(longAgo);
            
            when(memoryRepository.findByConflictGroupId(groupId))
                    .thenReturn(List.of(higherConfidence, lowerConfidence));

            ConflictResolution resolution = service.analyzeConflictGroup(groupId);

            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.EXPIRE);
            assertThat(resolution.affectedMemories()).contains(lowerConfidence);
            assertThat(resolution.affectedMemories()).doesNotContain(higherConfidence);
        }

        @Test
        @DisplayName("Waits for clarification when both recently accessed")
        void waitsForClarificationWhenBothRecentlyAccessed() {
            UUID groupId = UUID.randomUUID();
            Instant now = Instant.now();
            Instant recent = now.minusSeconds(5 * 24 * 60 * 60); // 5 days ago
            
            Memory memory1 = createMemory("Memory 1", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            memory1.setConflictGroupId(groupId);
            memory1.setLastAccessedAt(recent);
            
            Memory memory2 = createMemory("Memory 2", MemoryCategory.PREFERENCE, new BigDecimal("0.7"));
            memory2.setConflictGroupId(groupId);
            memory2.setLastAccessedAt(recent);
            
            when(memoryRepository.findByConflictGroupId(groupId))
                    .thenReturn(List.of(memory1, memory2));

            ConflictResolution resolution = service.analyzeConflictGroup(groupId);

            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.WAIT_FOR_CLARIFICATION);
            assertThat(resolution.affectedMemories()).containsExactlyInAnyOrder(memory1, memory2);
        }

        @Test
        @DisplayName("Handles null lastAccessedAt as long unaccessed")
        void handlesNullLastAccessedAt() {
            UUID groupId = UUID.randomUUID();
            Instant now = Instant.now();
            Instant longAgo = now.minusSeconds(35 * 24 * 60 * 60); // 35 days ago
            
            Memory withAccess = createMemory("With access", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            withAccess.setConflictGroupId(groupId);
            withAccess.setLastAccessedAt(longAgo);
            
            Memory withoutAccess = createMemory("Without access", MemoryCategory.PREFERENCE, new BigDecimal("0.6"));
            withoutAccess.setConflictGroupId(groupId);
            withoutAccess.setLastAccessedAt(null); // Never accessed
            
            when(memoryRepository.findByConflictGroupId(groupId))
                    .thenReturn(List.of(withAccess, withoutAccess));

            ConflictResolution resolution = service.analyzeConflictGroup(groupId);

            // Both should be considered long unaccessed, so expire lowest confidence
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.EXPIRE);
            assertThat(resolution.affectedMemories()).contains(withoutAccess);
        }
    }

    // ─── Conflict Resolution Application Tests ───────────────────────────

    @Nested
    @DisplayName("Conflict Resolution Application")
    class ConflictResolutionApplicationTests {

        @Test
        @DisplayName("Applies SUPERSEDE resolution correctly")
        void appliesSupersedeResolutionCorrectly() {
            UUID groupId = UUID.randomUUID();
            
            Memory winner = createMemory("Winner", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            winner.setConflictGroupId(groupId);
            
            Memory loser = createMemory("Loser", MemoryCategory.PREFERENCE, new BigDecimal("0.6"));
            loser.setConflictGroupId(groupId);
            
            ConflictResolution resolution = ConflictResolution.supersedeMemories(
                    groupId, winner, List.of(loser), "Winner was more recent");

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            service.applyResolution(resolution);

            // Verify loser was superseded and saved
            verify(memoryRepository, atLeastOnce()).save(argThat(memory -> 
                    memory.getId().equals(loser.getId()) && 
                    memory.getState() == MemoryState.SUPERSEDED &&
                    memory.getConflictGroupId() == null));
            
            // Verify winner had conflict group cleared
            verify(memoryRepository, atLeastOnce()).save(argThat(memory -> 
                    memory.getId().equals(winner.getId()) && 
                    memory.getConflictGroupId() == null));
        }

        @Test
        @DisplayName("Applies EXPIRE resolution correctly")
        void appliesExpireResolutionCorrectly() {
            UUID groupId = UUID.randomUUID();
            
            Memory toExpire = createMemory("To expire", MemoryCategory.PREFERENCE, new BigDecimal("0.6"));
            toExpire.setConflictGroupId(groupId);
            
            ConflictResolution resolution = ConflictResolution.expireMemories(
                    groupId, List.of(toExpire), "Not accessed in 30+ days");

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            service.applyResolution(resolution);

            verify(memoryRepository).save(argThat(memory -> 
                    memory.getId().equals(toExpire.getId()) && 
                    memory.getState() == MemoryState.EXPIRED &&
                    memory.getConflictGroupId() == null));
        }

        @Test
        @DisplayName("Applies CLEAR_GROUP resolution correctly")
        void appliesClearGroupResolutionCorrectly() {
            UUID groupId = UUID.randomUUID();
            
            Memory remaining = createMemory("Remaining", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            remaining.setConflictGroupId(groupId);
            
            ConflictResolution resolution = ConflictResolution.clearConflictGroup(
                    groupId, remaining, "Only one memory remains");

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            service.applyResolution(resolution);

            verify(memoryRepository).save(argThat(memory -> 
                    memory.getId().equals(remaining.getId()) && 
                    memory.getConflictGroupId() == null));
        }

        @Test
        @DisplayName("Does not modify memories for WAIT_FOR_CLARIFICATION")
        void doesNotModifyForWaitForClarification() {
            UUID groupId = UUID.randomUUID();
            
            Memory memory1 = createMemory("Memory 1", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            Memory memory2 = createMemory("Memory 2", MemoryCategory.PREFERENCE, new BigDecimal("0.7"));
            
            ConflictResolution resolution = ConflictResolution.waitForClarification(
                    groupId, List.of(memory1, memory2), "Both recently accessed");

            service.applyResolution(resolution);

            verify(memoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Does not modify memories for NO_ACTION")
        void doesNotModifyForNoAction() {
            UUID groupId = UUID.randomUUID();
            ConflictResolution resolution = ConflictResolution.noAction(groupId, "Empty group");

            service.applyResolution(resolution);

            verify(memoryRepository, never()).save(any());
        }
    }

    // ─── Multiple Contradictions Grouping Tests ──────────────────────────

    @Nested
    @DisplayName("Multiple Contradictions Grouping")
    class MultipleContradictionsGroupingTests {

        @Test
        @DisplayName("Groups multiple contradictions into appropriate groups")
        void groupsMultipleContradictions() {
            Memory existing1 = createMemory("Memory 1", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            Memory new1 = createMemory("New 1", MemoryCategory.PREFERENCE, new BigDecimal("0.7"));
            Memory existing2 = createMemory("Memory 2", MemoryCategory.IDENTITY, new BigDecimal("0.9"));
            Memory new2 = createMemory("New 2", MemoryCategory.IDENTITY, new BigDecimal("0.6"));

            List<Contradiction> contradictions = List.of(
                    createContradiction(existing1, new1),
                    createContradiction(existing2, new2)
            );

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            Map<UUID, List<UUID>> result = service.groupConflictingMemories(contradictions);

            assertThat(result).hasSize(2);
            result.values().forEach(ids -> assertThat(ids).hasSize(2));
        }

        @Test
        @DisplayName("Handles empty contradictions list")
        void handlesEmptyContradictionsList() {
            Map<UUID, List<UUID>> result = service.groupConflictingMemories(Collections.emptyList());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Handles null contradictions list")
        void handlesNullContradictionsList() {
            Map<UUID, List<UUID>> result = service.groupConflictingMemories((List<Contradiction>) null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Skips ineligible memories in batch")
        void skipsIneligibleMemoriesInBatch() {
            Memory eligible1 = createMemory("Eligible 1", MemoryCategory.PREFERENCE, new BigDecimal("0.8"));
            Memory eligible2 = createMemory("Eligible 2", MemoryCategory.PREFERENCE, new BigDecimal("0.7"));
            Memory ineligible = createMemory("Ineligible", MemoryCategory.IDENTITY, new BigDecimal("0.3")); // Low confidence
            Memory eligibleForIneligible = createMemory("For ineligible", MemoryCategory.IDENTITY, new BigDecimal("0.8"));

            List<Contradiction> contradictions = List.of(
                    createContradiction(eligible1, eligible2),
                    createContradiction(ineligible, eligibleForIneligible)
            );

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            Map<UUID, List<UUID>> result = service.groupConflictingMemories(contradictions);

            // Only the first contradiction should create a group
            assertThat(result).hasSize(1);
        }
    }

    // ─── Eligibility Check Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("Eligibility Check")
    class EligibilityCheckTests {

        @Test
        @DisplayName("Memory with ACTIVE state and sufficient confidence is eligible")
        void activeMemoryWithSufficientConfidenceIsEligible() {
            Memory memory = createMemory("Test", MemoryCategory.PREFERENCE, new BigDecimal("0.6"));
            
            assertThat(service.isEligibleForConflictGroup(memory)).isTrue();
        }

        @Test
        @DisplayName("Memory with CONFIRMED state and sufficient confidence is eligible")
        void confirmedMemoryWithSufficientConfidenceIsEligible() {
            Memory memory = createMemory("Test", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.9"), MemoryState.CONFIRMED);
            
            assertThat(service.isEligibleForConflictGroup(memory)).isTrue();
        }

        @Test
        @DisplayName("Memory with confidence below 0.5 is not eligible")
        void lowConfidenceMemoryIsNotEligible() {
            Memory memory = createMemory("Test", MemoryCategory.PREFERENCE, new BigDecimal("0.49"));
            
            assertThat(service.isEligibleForConflictGroup(memory)).isFalse();
        }

        @Test
        @DisplayName("Memory in EXPIRED state is not eligible")
        void expiredMemoryIsNotEligible() {
            Memory memory = createMemory("Test", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.8"), MemoryState.EXPIRED);
            
            assertThat(service.isEligibleForConflictGroup(memory)).isFalse();
        }

        @Test
        @DisplayName("Memory in DELETED state is not eligible")
        void deletedMemoryIsNotEligible() {
            Memory memory = createMemory("Test", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.8"), MemoryState.DELETED);
            
            assertThat(service.isEligibleForConflictGroup(memory)).isFalse();
        }

        @Test
        @DisplayName("Memory in ARCHIVED state is not eligible")
        void archivedMemoryIsNotEligible() {
            Memory memory = createMemory("Test", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.8"), MemoryState.ARCHIVED);
            
            assertThat(service.isEligibleForConflictGroup(memory)).isFalse();
        }

        @Test
        @DisplayName("Null memory is not eligible")
        void nullMemoryIsNotEligible() {
            assertThat(service.isEligibleForConflictGroup(null)).isFalse();
        }
    }

    // ─── Newer Higher Confidence Resolution Tests ────────────────────────

    /**
     * Tests for SPEC-004 Requirement 7, Task 8 criteria 3:
     * "Newer memory with higher confidence wins (supersedes older)"
     */
    @Nested
    @DisplayName("Newer Higher Confidence Resolution")
    class NewerHigherConfidenceResolutionTests {

        private Memory createMemoryWithTimestamp(String content, MemoryCategory category, 
                                                  BigDecimal confidence, Instant createdAt) {
            Memory memory = createMemory(content, category, confidence);
            memory.setCreatedAt(createdAt);
            return memory;
        }

        @Test
        @DisplayName("Newer memory with higher confidence supersedes older memory")
        void newerMemoryWithHigherConfidenceSupersedesOlder() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60); // 1 day ago
            
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.80"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            ConflictResolution resolution = service.resolveByNewerHigherConfidence(contradiction);

            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.SUPERSEDE);
            assertThat(resolution.winningMemory()).isEqualTo(newerMemory);
            assertThat(resolution.affectedMemories()).contains(olderMemory);
        }

        @Test
        @DisplayName("Returns null when newer memory has lower confidence")
        void returnsNullWhenNewerMemoryHasLowerConfidence() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.90"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            ConflictResolution resolution = service.resolveByNewerHigherConfidence(contradiction);

            assertThat(resolution).isNull();
        }

        @Test
        @DisplayName("Returns null when both memories have equal confidence")
        void returnsNullWhenEqualConfidence() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.70"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.70"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            ConflictResolution resolution = service.resolveByNewerHigherConfidence(contradiction);

            assertThat(resolution).isNull();
        }

        @Test
        @DisplayName("Returns null when memories have same creation time")
        void returnsNullWhenSameCreationTime() {
            Instant sameTime = Instant.now();
            
            Memory memory1 = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), sameTime);
            Memory memory2 = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.80"), sameTime);
            
            Contradiction contradiction = createContradiction(memory1, memory2);

            ConflictResolution resolution = service.resolveByNewerHigherConfidence(contradiction);

            assertThat(resolution).isNull();
        }

        @Test
        @DisplayName("Correctly identifies newer memory when existing is newer")
        void correctlyIdentifiesNewerWhenExistingIsNewer() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            // In this case, the "existing" memory is actually newer
            Memory newerExisting = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.85"), now);
            Memory olderNew = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), olderTime);
            
            Contradiction contradiction = createContradiction(newerExisting, olderNew);

            ConflictResolution resolution = service.resolveByNewerHigherConfidence(contradiction);

            assertThat(resolution).isNotNull();
            assertThat(resolution.winningMemory()).isEqualTo(newerExisting);
            assertThat(resolution.affectedMemories()).contains(olderNew);
        }

        @Test
        @DisplayName("Throws exception for null contradiction")
        void throwsExceptionForNullContradiction() {
            assertThatThrownBy(() -> service.resolveByNewerHigherConfidence(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contradiction cannot be null");
        }

        @Test
        @DisplayName("Resolution reason includes confidence values")
        void resolutionReasonIncludesConfidenceValues() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.50"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.90"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            ConflictResolution resolution = service.resolveByNewerHigherConfidence(contradiction);

            assertThat(resolution.reason()).contains("confidence=0.90");
            assertThat(resolution.reason()).contains("confidence=0.50");
        }
    }

    // ─── Process Contradiction Tests ─────────────────────────────────────

    @Nested
    @DisplayName("Process Contradiction")
    class ProcessContradictionTests {

        private Memory createMemoryWithTimestamp(String content, MemoryCategory category, 
                                                  BigDecimal confidence, Instant createdAt) {
            Memory memory = createMemory(content, category, confidence);
            memory.setCreatedAt(createdAt);
            return memory;
        }

        @Test
        @DisplayName("Auto-resolves when newer memory has higher confidence")
        void autoResolvesWhenNewerHasHigherConfidence() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.80"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            ConflictResolution resolution = service.processContradiction(contradiction);

            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.SUPERSEDE);
            
            // Verify older memory was superseded
            verify(memoryRepository, atLeastOnce()).save(argThat(memory ->
                    memory.getId().equals(olderMemory.getId()) &&
                    memory.getState() == MemoryState.SUPERSEDED));
        }

        @Test
        @DisplayName("Flags memories when newer has lower confidence but decay makes them equal")
        void flagsMemoriesWhenNewerHasLowerConfidenceButDecayMakesThemEqual() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            // Older memory starts at 0.90, after 0.30 decay becomes 0.60
            // Newer memory is 0.60
            // After decay, both are 0.60 - similar confidence rule applies
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.90"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            ConflictResolution resolution = service.processContradiction(contradiction);

            // After decay (0.90 -> 0.60), both memories have equal confidence (0.60)
            // Similar confidence rule applies - both flagged for confirmation
            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.WAIT_FOR_CLARIFICATION);
            
            // Verify older memory's confidence was reduced
            assertThat(olderMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.60"));
        }

        @Test
        @DisplayName("Supersedes older memory when equal confidence and decay makes newer higher")
        void supersedesWhenEqualConfidenceAndDecayMakesNewerHigher() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            // Older memory starts at 0.75, after 0.30 decay becomes 0.45
            // Newer memory is 0.75
            // After decay, newer (0.75) > older (0.45), so newer-higher-confidence rule applies
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.75"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.75"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            ConflictResolution resolution = service.processContradiction(contradiction);

            // After decay (0.75 -> 0.45), newer (0.75) has higher confidence than older (0.45)
            // Newer-higher-confidence rule applies - older is superseded
            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.SUPERSEDE);
            
            // Verify older memory's confidence was reduced
            assertThat(olderMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.45"));
        }

        @Test
        @DisplayName("Throws exception for null contradiction")
        void throwsExceptionForNullContradiction() {
            assertThatThrownBy(() -> service.processContradiction(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contradiction cannot be null");
        }

        @Test
        @DisplayName("Supersedes older memory when decay drops it below 0.3 even with similar confidence start")
        void supersedesWhenDecayDropsBelowThresholdEvenWithSimilarStart() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            // Older memory starts at 0.40, after 0.30 decay becomes 0.10 (below 0.3)
            // Newer memory is 0.30
            // After decay, older's confidence (0.10) is below 0.30, triggering auto-supersession
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.40"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.30"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            ConflictResolution resolution = service.processContradiction(contradiction);

            // After decay (0.40 -> 0.10), older's confidence dropped below 0.3
            // Auto-supersession due to low confidence applies
            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.SUPERSEDE);
            assertThat(resolution.reason()).contains("dropped below 0.3");
            
            // Verify older memory's confidence was reduced
            assertThat(olderMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.10"));
        }
    }

    // ─── Similar Confidence Resolution Tests ─────────────────────────────

    /**
     * Tests for SPEC-004 Requirement 7, Task 8 criteria 4:
     * "Both memories kept if confidence is similar (flagged for confirmation)"
     *
     * <p>When two conflicting memories have similar confidence scores (difference < 0.15),
     * both should be kept, grouped under a conflict_group_id, and flagged for user confirmation.
     */
    @Nested
    @DisplayName("Similar Confidence Resolution")
    class SimilarConfidenceResolutionTests {

        private Memory createMemoryWithTimestamp(String content, MemoryCategory category, 
                                                  BigDecimal confidence, Instant createdAt) {
            Memory memory = createMemory(content, category, confidence);
            memory.setCreatedAt(createdAt);
            return memory;
        }

        @Test
        @DisplayName("Returns WAIT_FOR_CLARIFICATION when confidence difference is exactly 0")
        void returnsWaitForClarificationWhenConfidenceIdentical() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.70"));
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.70"));
            Contradiction contradiction = createContradiction(existing, newMemory);

            ConflictResolution resolution = service.resolveBySimilarConfidence(contradiction);

            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.WAIT_FOR_CLARIFICATION);
            assertThat(resolution.affectedMemories()).containsExactlyInAnyOrder(existing, newMemory);
            assertThat(resolution.reason()).contains("Similar confidence scores");
        }

        @Test
        @DisplayName("Returns WAIT_FOR_CLARIFICATION when confidence difference is less than threshold")
        void returnsWaitForClarificationWhenConfidenceSimilar() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.75"));
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.65"));
            // Difference is 0.10, which is less than 0.15 threshold
            Contradiction contradiction = createContradiction(existing, newMemory);

            ConflictResolution resolution = service.resolveBySimilarConfidence(contradiction);

            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.WAIT_FOR_CLARIFICATION);
            assertThat(resolution.reason()).contains("difference=0.10");
            assertThat(resolution.reason()).contains("threshold=0.15");
        }

        @Test
        @DisplayName("Returns null when confidence difference equals threshold")
        void returnsNullWhenConfidenceDifferenceEqualsThreshold() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.80"));
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.65"));
            // Difference is 0.15, which equals the threshold
            Contradiction contradiction = createContradiction(existing, newMemory);

            ConflictResolution resolution = service.resolveBySimilarConfidence(contradiction);

            // At the threshold (not less than), should return null
            assertThat(resolution).isNull();
        }

        @Test
        @DisplayName("Returns null when confidence difference exceeds threshold")
        void returnsNullWhenConfidenceDifferenceExceedsThreshold() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.90"));
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.60"));
            // Difference is 0.30, which exceeds 0.15 threshold
            Contradiction contradiction = createContradiction(existing, newMemory);

            ConflictResolution resolution = service.resolveBySimilarConfidence(contradiction);

            assertThat(resolution).isNull();
        }

        @Test
        @DisplayName("Handles similar confidence when new memory is higher")
        void handlesSimilarConfidenceWhenNewMemoryHigher() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.70"));
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.80"));
            // Difference is 0.10 (new is higher)
            Contradiction contradiction = createContradiction(existing, newMemory);

            ConflictResolution resolution = service.resolveBySimilarConfidence(contradiction);

            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.WAIT_FOR_CLARIFICATION);
        }

        @Test
        @DisplayName("Throws exception for null contradiction")
        void throwsExceptionForNullContradiction() {
            assertThatThrownBy(() -> service.resolveBySimilarConfidence(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contradiction cannot be null");
        }

        @Test
        @DisplayName("Resolution includes both confidence values in reason")
        void resolutionIncludesBothConfidenceValuesInReason() {
            Memory existing = createMemory("Lucas likes broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.72"));
            Memory newMemory = createMemory("Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, new BigDecimal("0.78"));
            Contradiction contradiction = createContradiction(existing, newMemory);

            ConflictResolution resolution = service.resolveBySimilarConfidence(contradiction);

            assertThat(resolution.reason()).contains("existing=0.72");
            assertThat(resolution.reason()).contains("new=0.78");
        }
    }

    // ─── Flag Memories For User Confirmation Tests ───────────────────────

    @Nested
    @DisplayName("Flag Memories For User Confirmation")
    class FlagMemoriesForUserConfirmationTests {

        @Test
        @DisplayName("Flags both memories for user confirmation")
        void flagsBothMemoriesForUserConfirmation() {
            UUID conflictGroupId = UUID.randomUUID();
            Memory memory1 = createMemory("Memory 1", MemoryCategory.PREFERENCE, new BigDecimal("0.70"));
            Memory memory2 = createMemory("Memory 2", MemoryCategory.PREFERENCE, new BigDecimal("0.72"));

            ConflictResolution resolution = ConflictResolution.waitForClarification(
                    conflictGroupId, List.of(memory1, memory2), "Similar confidence");

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            UUID resultGroupId = service.flagMemoriesForUserConfirmation(resolution);

            assertThat(resultGroupId).isEqualTo(conflictGroupId);

            // Verify both memories were saved with needsUserConfirmation=true
            ArgumentCaptor<Memory> memoryCaptor = ArgumentCaptor.forClass(Memory.class);
            verify(memoryRepository, times(2)).save(memoryCaptor.capture());

            List<Memory> savedMemories = memoryCaptor.getAllValues();
            assertThat(savedMemories).allMatch(Memory::getNeedsUserConfirmation);
            assertThat(savedMemories).allMatch(m -> m.getConflictGroupId().equals(conflictGroupId));
        }

        @Test
        @DisplayName("Throws exception for null resolution")
        void throwsExceptionForNullResolution() {
            assertThatThrownBy(() -> service.flagMemoriesForUserConfirmation(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("resolution cannot be null");
        }

        @Test
        @DisplayName("Throws exception for non-WAIT_FOR_CLARIFICATION resolution")
        void throwsExceptionForWrongResolutionAction() {
            UUID conflictGroupId = UUID.randomUUID();
            Memory winner = createMemory("Winner", MemoryCategory.PREFERENCE, new BigDecimal("0.90"));
            Memory loser = createMemory("Loser", MemoryCategory.PREFERENCE, new BigDecimal("0.60"));

            ConflictResolution resolution = ConflictResolution.supersedeMemories(
                    conflictGroupId, winner, List.of(loser), "Test");

            assertThatThrownBy(() -> service.flagMemoriesForUserConfirmation(resolution))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("WAIT_FOR_CLARIFICATION");
        }
    }

    // ─── Has Similar Confidence Tests ────────────────────────────────────

    @Nested
    @DisplayName("Has Similar Confidence")
    class HasSimilarConfidenceTests {

        @Test
        @DisplayName("Returns true when confidence is identical")
        void returnsTrueWhenConfidenceIdentical() {
            Memory memory1 = createMemory("Memory 1", MemoryCategory.PREFERENCE, new BigDecimal("0.70"));
            Memory memory2 = createMemory("Memory 2", MemoryCategory.PREFERENCE, new BigDecimal("0.70"));

            assertThat(service.hasSimilarConfidence(memory1, memory2)).isTrue();
        }

        @Test
        @DisplayName("Returns true when difference is less than threshold")
        void returnsTrueWhenDifferenceLessThanThreshold() {
            Memory memory1 = createMemory("Memory 1", MemoryCategory.PREFERENCE, new BigDecimal("0.70"));
            Memory memory2 = createMemory("Memory 2", MemoryCategory.PREFERENCE, new BigDecimal("0.80"));
            // Difference is 0.10 < 0.15

            assertThat(service.hasSimilarConfidence(memory1, memory2)).isTrue();
        }

        @Test
        @DisplayName("Returns false when difference equals threshold")
        void returnsFalseWhenDifferenceEqualsThreshold() {
            Memory memory1 = createMemory("Memory 1", MemoryCategory.PREFERENCE, new BigDecimal("0.70"));
            Memory memory2 = createMemory("Memory 2", MemoryCategory.PREFERENCE, new BigDecimal("0.85"));
            // Difference is 0.15

            assertThat(service.hasSimilarConfidence(memory1, memory2)).isFalse();
        }

        @Test
        @DisplayName("Returns false when difference exceeds threshold")
        void returnsFalseWhenDifferenceExceedsThreshold() {
            Memory memory1 = createMemory("Memory 1", MemoryCategory.PREFERENCE, new BigDecimal("0.50"));
            Memory memory2 = createMemory("Memory 2", MemoryCategory.PREFERENCE, new BigDecimal("0.90"));
            // Difference is 0.40 > 0.15

            assertThat(service.hasSimilarConfidence(memory1, memory2)).isFalse();
        }

        @Test
        @DisplayName("Returns false for null first memory")
        void returnsFalseForNullFirstMemory() {
            Memory memory2 = createMemory("Memory 2", MemoryCategory.PREFERENCE, new BigDecimal("0.70"));

            assertThat(service.hasSimilarConfidence(null, memory2)).isFalse();
        }

        @Test
        @DisplayName("Returns false for null second memory")
        void returnsFalseForNullSecondMemory() {
            Memory memory1 = createMemory("Memory 1", MemoryCategory.PREFERENCE, new BigDecimal("0.70"));

            assertThat(service.hasSimilarConfidence(memory1, null)).isFalse();
        }

        @Test
        @DisplayName("Returns false for both null")
        void returnsFalseForBothNull() {
            assertThat(service.hasSimilarConfidence(null, null)).isFalse();
        }
    }

    // ─── Process Contradiction with Similar Confidence Tests ─────────────

    /**
     * Tests for process contradiction with similar confidence scenarios,
     * accounting for confidence decay being applied first.
     *
     * <p>IMPORTANT: These tests account for confidence decay (0.3) being applied
     * to the older memory BEFORE resolution rules are evaluated.
     */
    @Nested
    @DisplayName("Process Contradiction with Similar Confidence (with decay)")
    class ProcessContradictionSimilarConfidenceTests {

        private Memory createMemoryWithTimestamp(String content, MemoryCategory category, 
                                                  BigDecimal confidence, Instant createdAt) {
            Memory memory = createMemory(content, category, confidence);
            memory.setCreatedAt(createdAt);
            return memory;
        }

        @Test
        @DisplayName("Supersedes older when decay makes newer have higher confidence")
        void supersedesWhenDecayMakesNewerHigherConfidence() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);

            // Older: 0.75, after decay: 0.45
            // Newer: 0.70
            // After decay, newer (0.70) > older (0.45), so newer wins
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.75"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.70"), now);

            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            ConflictResolution resolution = service.processContradiction(contradiction);

            // After decay, newer has higher confidence -> SUPERSEDE
            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.SUPERSEDE);
            assertThat(olderMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.45"));
        }

        @Test
        @DisplayName("Supersedes older memory when newer has higher confidence even if similar")
        void supersedesWhenNewerHasHigherConfidenceEvenIfSimilar() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);

            // Older: 0.70, after decay: 0.40
            // Newer: 0.80
            // After decay, newer (0.80) > older (0.40), so newer wins
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.70"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.80"), now);

            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            ConflictResolution resolution = service.processContradiction(contradiction);

            // Should SUPERSEDE because newer has higher confidence after decay
            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.SUPERSEDE);
            assertThat(olderMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.40"));
        }

        @Test
        @DisplayName("Flags memories when decay brings them to similar confidence")
        void flagsMemoriesWhenDecayBringsThemToSimilarConfidence() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);

            // Older: 0.90, after decay: 0.60
            // Newer: 0.60
            // After decay, both have 0.60 - similar confidence rule applies
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.90"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), now);

            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            ConflictResolution resolution = service.processContradiction(contradiction);

            // After decay, both have 0.60 - similar confidence -> WAIT_FOR_CLARIFICATION
            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.WAIT_FOR_CLARIFICATION);
            assertThat(olderMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.60"));
        }
    }

    // ─── Confidence Decay on Contradiction Tests ─────────────────────────

    /**
     * Tests for SPEC-004 Requirement 7, Task 8 final criteria:
     * "Contradiction detected → confidence of older memory reduced by 0.3"
     *
     * <p>Per Requirement 7 criteria 3:
     * WHEN an implicit contradiction is detected (father states something different without correction language),
     * THE Memory_System SHALL reduce the older memory's confidence by 0.3 (minimum 0.0).
     */
    @Nested
    @DisplayName("Confidence Decay on Contradiction")
    class ConfidenceDecayOnContradictionTests {

        private Memory createMemoryWithTimestamp(String content, MemoryCategory category, 
                                                  BigDecimal confidence, Instant createdAt) {
            Memory memory = createMemory(content, category, confidence);
            memory.setCreatedAt(createdAt);
            return memory;
        }

        @Test
        @DisplayName("Reduces older memory confidence by 0.3 when contradiction detected")
        void reducesOlderMemoryConfidenceWhenContradictionDetected() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60); // 1 day ago
            
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.80"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            service.applyConfidenceDecayToOlderMemory(contradiction);

            // Verify older memory's confidence was reduced by 0.3: 0.80 - 0.30 = 0.50
            assertThat(olderMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.50"));
            verify(memoryRepository).save(olderMemory);
        }

        @Test
        @DisplayName("Confidence decay does not go below 0.0")
        void confidenceDecayDoesNotGoBelowZero() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.20"), olderTime); // Starting below 0.3
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            service.applyConfidenceDecayToOlderMemory(contradiction);

            // Verify confidence is capped at 0.0: 0.20 - 0.30 = -0.10 → 0.00
            assertThat(olderMemory.getConfidenceScore()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Confidence decay results in exactly 0.0 when starting at 0.30")
        void confidenceDecayResultsInZeroWhenStartingAtThreshold() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.30"), olderTime); // Exactly at decay amount
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            service.applyConfidenceDecayToOlderMemory(contradiction);

            // Verify confidence is exactly 0.0: 0.30 - 0.30 = 0.00
            assertThat(olderMemory.getConfidenceScore()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Decay is applied to existing memory when it is older")
        void decayAppliedToExistingMemoryWhenOlder() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            // existing is older
            Memory existingMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.70"), olderTime);
            Memory newMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), now);
            
            Contradiction contradiction = createContradiction(existingMemory, newMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            service.applyConfidenceDecayToOlderMemory(contradiction);

            // Verify existing (older) memory's confidence was reduced
            assertThat(existingMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.40"));
            // Verify new memory's confidence unchanged
            assertThat(newMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.60"));
        }

        @Test
        @DisplayName("Decay is applied to new memory when it is older")
        void decayAppliedToNewMemoryWhenOlder() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            // new memory is older (existing is newer - unusual but possible)
            Memory existingMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), now);
            Memory newMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.70"), olderTime);
            
            Contradiction contradiction = createContradiction(existingMemory, newMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            service.applyConfidenceDecayToOlderMemory(contradiction);

            // Verify new (older) memory's confidence was reduced
            assertThat(newMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.40"));
            // Verify existing memory's confidence unchanged
            assertThat(existingMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.60"));
        }

        @Test
        @DisplayName("No decay applied when both memories have same creation time")
        void noDecayWhenSameCreationTime() {
            Instant sameTime = Instant.now();
            
            Memory existingMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.80"), sameTime);
            Memory newMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.70"), sameTime);
            
            Contradiction contradiction = createContradiction(existingMemory, newMemory);

            service.applyConfidenceDecayToOlderMemory(contradiction);

            // Verify neither memory's confidence was changed
            assertThat(existingMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.80"));
            assertThat(newMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.70"));
            // Verify no save was called
            verify(memoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws exception for null contradiction")
        void throwsExceptionForNullContradiction() {
            assertThatThrownBy(() -> service.applyConfidenceDecayToOlderMemory(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contradiction cannot be null");
        }

        @Test
        @DisplayName("Confidence decay is applied before other resolution rules in processContradiction")
        void confidenceDecayAppliedBeforeOtherResolutionRules() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            // Older memory starts with higher confidence (0.70), newer has lower (0.60)
            // Without decay, similar confidence rule would apply (difference 0.10 < 0.15)
            // With decay, older becomes 0.40, newer is 0.60 - difference is now 0.20 >= 0.15
            // So memories should be grouped, not flagged for user confirmation
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.70"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            ConflictResolution resolution = service.processContradiction(contradiction);

            // After decay, older is 0.40, newer is 0.60
            // Newer doesn't have higher confidence than older after decay? Actually newer (0.60) > older (0.40)
            // So newer-higher-confidence rule applies!
            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.SUPERSEDE);
            
            // Verify the older memory's confidence was reduced
            assertThat(olderMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.40"));
        }

        @Test
        @DisplayName("Supersedes older memory when confidence drops below 0.3 after decay")
        void supersedesOlderMemoryWhenConfidenceDropsBelowThresholdAfterDecay() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            // Older memory starts at 0.50, after decay will be 0.20 (< 0.3)
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.50"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            ConflictResolution resolution = service.processContradiction(contradiction);

            // After decay, older is 0.20 (< 0.30), should be superseded
            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.SUPERSEDE);
            assertThat(resolution.reason()).contains("dropped below 0.3");
            
            // Verify the older memory's confidence was reduced
            assertThat(olderMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.20"));
        }

        @Test
        @DisplayName("Older memory confidence exactly at 0.30 after decay is not auto-superseded")
        void olderMemoryAtExactlyThresholdAfterDecayNotAutoSuperseded() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            // Older memory starts at 0.60, after decay will be exactly 0.30
            Memory olderMemory = createMemoryWithTimestamp(
                    "Lucas likes broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.60"), olderTime);
            Memory newerMemory = createMemoryWithTimestamp(
                    "Lucas doesn't like broccoli", MemoryCategory.PREFERENCE, 
                    new BigDecimal("0.70"), now);
            
            Contradiction contradiction = createContradiction(olderMemory, newerMemory);

            when(memoryRepository.save(any(Memory.class))).thenAnswer(i -> i.getArgument(0));

            ConflictResolution resolution = service.processContradiction(contradiction);

            // After decay, older is exactly 0.30 (not < 0.30), so auto-supersede due to low confidence doesn't apply
            // But newer (0.70) > older (0.30), so newer-higher-confidence rule applies
            assertThat(olderMemory.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.30"));
            assertThat(resolution).isNotNull();
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.SUPERSEDE);
            // Reason should be about newer-higher-confidence, not about dropping below threshold
            assertThat(resolution.reason()).doesNotContain("dropped below 0.3");
        }
    }

    // ─── Get Older/Newer Memory Helper Tests ─────────────────────────────

    @Nested
    @DisplayName("Get Older/Newer Memory Helper")
    class GetOlderNewerMemoryHelperTests {

        private Memory createMemoryWithTimestamp(String content, MemoryCategory category, 
                                                  BigDecimal confidence, Instant createdAt) {
            Memory memory = createMemory(content, category, confidence);
            memory.setCreatedAt(createdAt);
            return memory;
        }

        @Test
        @DisplayName("getOlderMemory returns existing when it is older")
        void getOlderMemoryReturnsExistingWhenOlder() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            Memory existingMemory = createMemoryWithTimestamp(
                    "Existing", MemoryCategory.PREFERENCE, new BigDecimal("0.70"), olderTime);
            Memory newMemory = createMemoryWithTimestamp(
                    "New", MemoryCategory.PREFERENCE, new BigDecimal("0.60"), now);
            
            Contradiction contradiction = createContradiction(existingMemory, newMemory);

            Memory older = service.getOlderMemory(contradiction);

            assertThat(older).isEqualTo(existingMemory);
        }

        @Test
        @DisplayName("getOlderMemory returns new when it is older")
        void getOlderMemoryReturnsNewWhenOlder() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            Memory existingMemory = createMemoryWithTimestamp(
                    "Existing", MemoryCategory.PREFERENCE, new BigDecimal("0.70"), now);
            Memory newMemory = createMemoryWithTimestamp(
                    "New", MemoryCategory.PREFERENCE, new BigDecimal("0.60"), olderTime);
            
            Contradiction contradiction = createContradiction(existingMemory, newMemory);

            Memory older = service.getOlderMemory(contradiction);

            assertThat(older).isEqualTo(newMemory);
        }

        @Test
        @DisplayName("getOlderMemory returns null when same creation time")
        void getOlderMemoryReturnsNullWhenSameTime() {
            Instant sameTime = Instant.now();
            
            Memory existingMemory = createMemoryWithTimestamp(
                    "Existing", MemoryCategory.PREFERENCE, new BigDecimal("0.70"), sameTime);
            Memory newMemory = createMemoryWithTimestamp(
                    "New", MemoryCategory.PREFERENCE, new BigDecimal("0.60"), sameTime);
            
            Contradiction contradiction = createContradiction(existingMemory, newMemory);

            Memory older = service.getOlderMemory(contradiction);

            assertThat(older).isNull();
        }

        @Test
        @DisplayName("getNewerMemory returns new when it is newer")
        void getNewerMemoryReturnsNewWhenNewer() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            Memory existingMemory = createMemoryWithTimestamp(
                    "Existing", MemoryCategory.PREFERENCE, new BigDecimal("0.70"), olderTime);
            Memory newMemory = createMemoryWithTimestamp(
                    "New", MemoryCategory.PREFERENCE, new BigDecimal("0.60"), now);
            
            Contradiction contradiction = createContradiction(existingMemory, newMemory);

            Memory newer = service.getNewerMemory(contradiction);

            assertThat(newer).isEqualTo(newMemory);
        }

        @Test
        @DisplayName("getNewerMemory returns existing when it is newer")
        void getNewerMemoryReturnsExistingWhenNewer() {
            Instant now = Instant.now();
            Instant olderTime = now.minusSeconds(24 * 60 * 60);
            
            Memory existingMemory = createMemoryWithTimestamp(
                    "Existing", MemoryCategory.PREFERENCE, new BigDecimal("0.70"), now);
            Memory newMemory = createMemoryWithTimestamp(
                    "New", MemoryCategory.PREFERENCE, new BigDecimal("0.60"), olderTime);
            
            Contradiction contradiction = createContradiction(existingMemory, newMemory);

            Memory newer = service.getNewerMemory(contradiction);

            assertThat(newer).isEqualTo(existingMemory);
        }

        @Test
        @DisplayName("getNewerMemory returns null when same creation time")
        void getNewerMemoryReturnsNullWhenSameTime() {
            Instant sameTime = Instant.now();
            
            Memory existingMemory = createMemoryWithTimestamp(
                    "Existing", MemoryCategory.PREFERENCE, new BigDecimal("0.70"), sameTime);
            Memory newMemory = createMemoryWithTimestamp(
                    "New", MemoryCategory.PREFERENCE, new BigDecimal("0.60"), sameTime);
            
            Contradiction contradiction = createContradiction(existingMemory, newMemory);

            Memory newer = service.getNewerMemory(contradiction);

            assertThat(newer).isNull();
        }
    }
}
