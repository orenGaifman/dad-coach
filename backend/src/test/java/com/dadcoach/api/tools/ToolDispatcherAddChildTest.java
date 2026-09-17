package com.dadcoach.api.tools;

import com.dadcoach.calendar.GoogleCalendarService;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the add_child tool handler in {@link ToolDispatcher}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ToolDispatcher add_child Tests")
class ToolDispatcherAddChildTest {

    @Mock private QualityTimeService qualityTimeService;
    @Mock private QualityTimeRepository qualityTimeRepository;
    @Mock private WeeklyGoalService weeklyGoalService;
    @Mock private GoogleCalendarService googleCalendarService;
    @Mock private SystemStateLoader systemStateLoader;
    @Mock private FatherRepository fatherRepository;
    @Mock private ChildRepository childRepository;

    private ToolDispatcher dispatcher;

    private static final Long FATHER_ID = 42L;

    @BeforeEach
    void setUp() {
        dispatcher = new ToolDispatcher(
                qualityTimeService,
                qualityTimeRepository,
                weeklyGoalService,
                googleCalendarService,
                systemStateLoader,
                fatherRepository,
                childRepository);
        dispatcher.initializeHandlers();
    }

    private ToolApiController.ResolvedToolRequest request(Map<String, Object> params) {
        return new ToolApiController.ResolvedToolRequest("exec-1", "idmp-1", FATHER_ID, params);
    }

    @Test
    @DisplayName("add_child is a registered tool")
    void addChildIsRegistered() {
        assertThat(dispatcher.getAvailableTools()).contains("add_child");
    }

    @Test
    @DisplayName("adds a child with all fields and returns the expected response")
    void addsChildSuccessfully() {
        Father father = new Father("+972503020551");
        when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
        when(childRepository.findByFatherIdAndStatus(FATHER_ID, "ACTIVE")).thenReturn(List.of());
        when(childRepository.countActiveByFatherId(FATHER_ID)).thenReturn(1L);
        when(childRepository.save(any(Child.class))).thenAnswer(inv -> inv.getArgument(0));

        ToolExecutionResponse response = dispatcher.dispatch("add_child", request(Map.of(
                "name", "Sarah",
                "age", 8,
                "gender", "girl",
                "interests", List.of("dancing", "drawing", "playing with her dog")
        )));

        assertThat(response.success()).isTrue();
        Map<String, Object> data = response.data();
        assertThat(data.get("name")).isEqualTo("Sarah");
        assertThat(data.get("age")).isEqualTo(8);
        assertThat(data.get("gender")).isEqualTo("girl");
        assertThat(data.get("status")).isEqualTo("added");
        assertThat(data.get("childCount")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        List<String> savedInterests = (List<String>) data.get("interests");
        assertThat(savedInterests).containsExactly("dancing", "drawing", "playing with her dog");
        verify(childRepository).save(any(Child.class));
    }

    @Test
    @DisplayName("infers no gender when omitted")
    void addsChildWithoutGender() {
        Father father = new Father("+972503020551");
        when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
        when(childRepository.findByFatherIdAndStatus(FATHER_ID, "ACTIVE")).thenReturn(List.of());
        when(childRepository.countActiveByFatherId(FATHER_ID)).thenReturn(1L);
        when(childRepository.save(any(Child.class))).thenAnswer(inv -> inv.getArgument(0));

        ToolExecutionResponse response = dispatcher.dispatch("add_child", request(Map.of(
                "name", "Noah",
                "age", 5
        )));

        assertThat(response.success()).isTrue();
        assertThat(response.data().get("gender")).isNull();
    }

    @Test
    @DisplayName("fails validation when name is missing")
    void failsWhenNameMissing() {
        ToolExecutionResponse response = dispatcher.dispatch("add_child", request(Map.of("age", 8)));

        assertThat(response.success()).isFalse();
        verify(childRepository, never()).save(any());
    }

    @Test
    @DisplayName("fails validation when age is out of range")
    void failsWhenAgeOutOfRange() {
        ToolExecutionResponse response = dispatcher.dispatch("add_child", request(Map.of(
                "name", "Sarah", "age", 40)));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_AGE");
        verify(childRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects invalid gender values")
    void rejectsInvalidGender() {
        Father father = new Father("+972503020551");
        when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));

        ToolExecutionResponse response = dispatcher.dispatch("add_child", request(Map.of(
                "name", "Sarah", "age", 8, "gender", "female")));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_GENDER");
        verify(childRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects a duplicate child name for the same father")
    void rejectsDuplicateName() {
        Father father = new Father("+972503020551");
        Child existing = new Child(father, "Sarah", java.time.LocalDate.now().minusYears(8));
        when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
        when(childRepository.findByFatherIdAndStatus(FATHER_ID, "ACTIVE")).thenReturn(List.of(existing));

        ToolExecutionResponse response = dispatcher.dispatch("add_child", request(Map.of(
                "name", "sarah", "age", 8)));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("DUPLICATE_CHILD");
        verify(childRepository, never()).save(any());
    }
}
