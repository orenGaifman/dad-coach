package com.dadcoach.api.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dadcoach.calendar.GoogleCalendarService;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.weeklygoal.WeeklyGoalService;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ToolDispatcher.
 * Verifies tool registration and routing works correctly.
 */
@ExtendWith(MockitoExtension.class)
class ToolDispatcherTest {

    @Mock
    private QualityTimeService qualityTimeService;
    @Mock
    private QualityTimeRepository qualityTimeRepository;
    @Mock
    private WeeklyGoalService weeklyGoalService;
    @Mock
    private GoogleCalendarService googleCalendarService;
    @Mock
    private SystemStateLoader systemStateLoader;
    @Mock
    private FatherRepository fatherRepository;
    @Mock
    private ChildRepository childRepository;

    @InjectMocks
    private ToolDispatcher toolDispatcher;

    private static final Long TEST_FATHER_ID = 1L;

    @Test
    @DisplayName("Should return all available tools after initialization")
    void shouldReturnAllAvailableToolsAfterInit() {
        // Initialize handlers
        toolDispatcher.initializeHandlers();
        
        Set<String> tools = toolDispatcher.getAvailableTools();
        
        // Verify all 16 tools are registered
        assertThat(tools).containsExactlyInAnyOrder(
                // Scheduling Tools
                "schedule_quality_time",
                "reschedule_quality_time",
                "cancel_quality_time",
                "complete_quality_time",
                "show_available_slots",
                // Weekly Goal Tools
                "set_weekly_goal",
                "get_weekly_goal_status",
                "show_weekly_summary",
                // Progress & Dashboard Tools
                "show_progress",
                "get_dashboard_link",
                // Activity & Communication Tools
                "get_activity_ideas",
                "greet",
                "show_help",
                "clarify",
                "connect_calendar",
                "get_upcoming_quality_time"
        );
    }

    @Test
    @DisplayName("Should return tool not found for unknown tool")
    void shouldReturnToolNotFoundForUnknownTool() {
        toolDispatcher.initializeHandlers();
        
        ToolExecutionRequest request = new ToolExecutionRequest(
                "exec-1", "idmp-1", TEST_FATHER_ID, Map.of());
        
        ToolExecutionResponse response = toolDispatcher.dispatch("unknown_tool", request);
        
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("TOOL_NOT_FOUND");
    }

    @Test
    @DisplayName("Should have 16 registered tools")
    void shouldHaveSixteenTools() {
        toolDispatcher.initializeHandlers();
        
        Set<String> tools = toolDispatcher.getAvailableTools();
        
        assertThat(tools).hasSize(16);
    }

    @Test
    @DisplayName("Scheduling tools should be registered")
    void schedulingToolsShouldBeRegistered() {
        toolDispatcher.initializeHandlers();
        
        Set<String> tools = toolDispatcher.getAvailableTools();
        
        assertThat(tools).contains(
            "schedule_quality_time",
            "reschedule_quality_time",
            "cancel_quality_time",
            "complete_quality_time",
            "show_available_slots"
        );
    }

    @Test
    @DisplayName("Weekly goal tools should be registered")
    void weeklyGoalToolsShouldBeRegistered() {
        toolDispatcher.initializeHandlers();
        
        Set<String> tools = toolDispatcher.getAvailableTools();
        
        assertThat(tools).contains(
            "set_weekly_goal",
            "get_weekly_goal_status",
            "show_weekly_summary"
        );
    }
}
