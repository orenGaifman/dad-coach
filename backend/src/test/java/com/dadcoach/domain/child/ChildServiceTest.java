package com.dadcoach.domain.child;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChildServiceTest {

    @Mock
    private ChildRepository childRepository;

    @Mock
    private FatherRepository fatherRepository;

    @InjectMocks
    private ChildService childService;

    private Father father;

    @BeforeEach
    void setUp() {
        father = new Father("+972501234567");
        father.setId(1L);
    }

    @Nested
    @DisplayName("createChild")
    class CreateChild {

        @Test
        @DisplayName("should create a child with valid inputs")
        void shouldCreateChildWithValidInputs() {
            LocalDate birthDate = LocalDate.now().minusYears(5);
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.countActiveByFatherId(1L)).thenReturn(0L);
            when(childRepository.save(any(Child.class))).thenAnswer(inv -> {
                Child c = inv.getArgument(0);
                c.setId(10L);
                return c;
            });

            Child result = childService.createChild(1L, "David", birthDate);

            assertThat(result.getName()).isEqualTo("David");
            assertThat(result.getBirthDate()).isEqualTo(birthDate);
            assertThat(result.getFather()).isEqualTo(father);
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            verify(childRepository).save(any(Child.class));
        }

        @Test
        @DisplayName("should create a child with optional fields")
        void shouldCreateChildWithOptionalFields() {
            LocalDate birthDate = LocalDate.now().minusYears(8);
            List<String> interests = List.of("soccer", "drawing");
            List<String> challenges = List.of("homework", "screen time");

            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.countActiveByFatherId(1L)).thenReturn(0L);
            when(childRepository.save(any(Child.class))).thenAnswer(inv -> inv.getArgument(0));

            Child result = childService.createChild(1L, "Yael", birthDate, "F", interests, challenges);

            assertThat(result.getName()).isEqualTo("Yael");
            assertThat(result.getGender()).isEqualTo("F");
            assertThat(result.getInterests()).containsExactly("soccer", "drawing");
            assertThat(result.getChallenges()).containsExactly("homework", "screen time");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when father not found")
        void shouldThrowWhenFatherNotFound() {
            when(fatherRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> childService.createChild(999L, "David", LocalDate.now().minusYears(5)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Father");
        }

        @Test
        @DisplayName("should throw BusinessRuleViolationException when max 8 children exceeded")
        void shouldThrowWhenMaxChildrenExceeded() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.countActiveByFatherId(1L)).thenReturn(8L);

            assertThatThrownBy(() -> childService.createChild(1L, "David", LocalDate.now().minusYears(5)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("MAX_CHILDREN_EXCEEDED");
        }

        @Test
        @DisplayName("should allow creating child when father has exactly 7 active children")
        void shouldAllowCreatingChildWith7Existing() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.countActiveByFatherId(1L)).thenReturn(7L);
            when(childRepository.save(any(Child.class))).thenAnswer(inv -> inv.getArgument(0));

            Child result = childService.createChild(1L, "David", LocalDate.now().minusYears(5));

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw when birth date is in the future")
        void shouldThrowWhenBirthDateInFuture() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.countActiveByFatherId(1L)).thenReturn(0L);

            assertThatThrownBy(() -> childService.createChild(1L, "David", LocalDate.now().plusDays(1)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("INVALID_BIRTH_DATE")
                    .hasMessageContaining("future");
        }

        @Test
        @DisplayName("should throw when child is older than 18 years")
        void shouldThrowWhenChildOlderThan18() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.countActiveByFatherId(1L)).thenReturn(0L);

            LocalDate tooOld = LocalDate.now().minusYears(19);

            assertThatThrownBy(() -> childService.createChild(1L, "David", tooOld))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("INVALID_BIRTH_DATE");
        }

        @Test
        @DisplayName("should accept child born today (age 0)")
        void shouldAcceptChildBornToday() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.countActiveByFatherId(1L)).thenReturn(0L);
            when(childRepository.save(any(Child.class))).thenAnswer(inv -> inv.getArgument(0));

            Child result = childService.createChild(1L, "Baby", LocalDate.now());

            assertThat(result.getBirthDate()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("should accept child exactly 18 years old")
        void shouldAcceptChildExactly18() {
            when(fatherRepository.findById(1L)).thenReturn(Optional.of(father));
            when(childRepository.countActiveByFatherId(1L)).thenReturn(0L);
            when(childRepository.save(any(Child.class))).thenAnswer(inv -> inv.getArgument(0));

            LocalDate eighteenYearsAgo = LocalDate.now().minusYears(18);
            Child result = childService.createChild(1L, "Teen", eighteenYearsAgo);

            assertThat(result.getBirthDate()).isEqualTo(eighteenYearsAgo);
        }
    }

    @Nested
    @DisplayName("updateChild")
    class UpdateChild {

        @Test
        @DisplayName("should update name when provided")
        void shouldUpdateName() {
            Child existing = new Child(father, "OldName", LocalDate.now().minusYears(5));
            existing.setId(10L);

            when(childRepository.findById(10L)).thenReturn(Optional.of(existing));
            when(childRepository.save(any(Child.class))).thenAnswer(inv -> inv.getArgument(0));

            Child result = childService.updateChild(10L, "NewName", null, null);

            assertThat(result.getName()).isEqualTo("NewName");
        }

        @Test
        @DisplayName("should update interests and challenges")
        void shouldUpdateInterestsAndChallenges() {
            Child existing = new Child(father, "David", LocalDate.now().minusYears(5));
            existing.setId(10L);

            when(childRepository.findById(10L)).thenReturn(Optional.of(existing));
            when(childRepository.save(any(Child.class))).thenAnswer(inv -> inv.getArgument(0));

            List<String> newInterests = List.of("music", "coding");
            List<String> newChallenges = List.of("focus");

            Child result = childService.updateChild(10L, null, newInterests, newChallenges);

            assertThat(result.getName()).isEqualTo("David"); // unchanged
            assertThat(result.getInterests()).containsExactly("music", "coding");
            assertThat(result.getChallenges()).containsExactly("focus");
        }

        @Test
        @DisplayName("should not modify fields when null is passed")
        void shouldNotModifyNullFields() {
            Child existing = new Child(father, "David", LocalDate.now().minusYears(5));
            existing.setId(10L);
            existing.setInterests(List.of("sports"));
            existing.setChallenges(List.of("bedtime"));

            when(childRepository.findById(10L)).thenReturn(Optional.of(existing));
            when(childRepository.save(any(Child.class))).thenAnswer(inv -> inv.getArgument(0));

            Child result = childService.updateChild(10L, null, null, null);

            assertThat(result.getName()).isEqualTo("David");
            assertThat(result.getInterests()).containsExactly("sports");
            assertThat(result.getChallenges()).containsExactly("bedtime");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when child not found")
        void shouldThrowWhenChildNotFound() {
            when(childRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> childService.updateChild(999L, "Name", null, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Child");
        }
    }

    @Nested
    @DisplayName("archiveChild")
    class ArchiveChild {

        @Test
        @DisplayName("should set status to ARCHIVED")
        void shouldSetStatusToArchived() {
            Child existing = new Child(father, "David", LocalDate.now().minusYears(5));
            existing.setId(10L);
            existing.setStatus("ACTIVE");

            when(childRepository.findById(10L)).thenReturn(Optional.of(existing));
            when(childRepository.save(any(Child.class))).thenAnswer(inv -> inv.getArgument(0));

            Child result = childService.archiveChild(10L);

            assertThat(result.getStatus()).isEqualTo("ARCHIVED");
        }

        @Test
        @DisplayName("should throw when child is already archived")
        void shouldThrowWhenAlreadyArchived() {
            Child existing = new Child(father, "David", LocalDate.now().minusYears(5));
            existing.setId(10L);
            existing.setStatus("ARCHIVED");

            when(childRepository.findById(10L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> childService.archiveChild(10L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("CHILD_ALREADY_ARCHIVED");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when child not found")
        void shouldThrowWhenChildNotFoundForArchive() {
            when(childRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> childService.archiveChild(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Child");
        }
    }

    @Nested
    @DisplayName("getChildrenByFather")
    class GetChildrenByFather {

        @Test
        @DisplayName("should return all children for a father")
        void shouldReturnAllChildren() {
            Child child1 = new Child(father, "Child1", LocalDate.now().minusYears(3));
            Child child2 = new Child(father, "Child2", LocalDate.now().minusYears(7));

            when(childRepository.findByFatherId(1L)).thenReturn(List.of(child1, child2));

            List<Child> result = childService.getChildrenByFather(1L);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list when no children exist")
        void shouldReturnEmptyListWhenNoChildren() {
            when(childRepository.findByFatherId(1L)).thenReturn(List.of());

            List<Child> result = childService.getChildrenByFather(1L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("validateBirthDate")
    class ValidateBirthDate {

        @Test
        @DisplayName("should accept birth date for newborn (today)")
        void shouldAcceptToday() {
            assertThatCode(() -> childService.validateBirthDate(LocalDate.now()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should accept birth date exactly 18 years ago")
        void shouldAcceptExactly18YearsAgo() {
            assertThatCode(() -> childService.validateBirthDate(LocalDate.now().minusYears(18)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should reject birth date in the future")
        void shouldRejectFutureDate() {
            assertThatThrownBy(() -> childService.validateBirthDate(LocalDate.now().plusDays(1)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("future");
        }

        @Test
        @DisplayName("should reject birth date more than 18 years ago")
        void shouldRejectOlderThan18() {
            assertThatThrownBy(() -> childService.validateBirthDate(LocalDate.now().minusYears(19)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("INVALID_BIRTH_DATE");
        }
    }
}
