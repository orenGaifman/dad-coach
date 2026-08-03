package com.dadcoach.workflow.message;

import com.dadcoach.systemstate.AvailableSlot;
import com.dadcoach.workflow.Belt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MessageContext}.
 * 
 * Tests the builder pattern, immutability, and utility methods.
 */
@DisplayName("MessageContext")
class MessageContextTest {
    
    @Nested
    @DisplayName("Builder")
    class BuilderTests {
        
        @Test
        @DisplayName("should create context with minimal fields")
        void shouldCreateContextWithMinimalFields() {
            MessageContext context = MessageContext.builder()
                .messageType(MessageType.WELCOME_GREETING)
                .fatherName("David")
                .build();
            
            assertThat(context.getMessageType()).isEqualTo(MessageType.WELCOME_GREETING);
            assertThat(context.getFatherName()).isEqualTo("David");
            assertThat(context.getLocale()).isEqualTo(MessageContext.DEFAULT_LOCALE);
            assertThat(context.getTimezone()).isEqualTo(MessageContext.DEFAULT_TIMEZONE);
        }
        
        @Test
        @DisplayName("should create context with all fields")
        void shouldCreateContextWithAllFields() {
            Instant now = Instant.now();
            Instant later = now.plusSeconds(1800);
            AvailableSlot slot = AvailableSlot.of(now, later);
            MessageContext.ActivityIdea idea = new MessageContext.ActivityIdea(
                "Build with LEGO", "Build a tower together", 30, true
            );
            
            MessageContext context = MessageContext.builder()
                .messageType(MessageType.SCHEDULE_SLOTS)
                .fatherName("David")
                .locale("he")
                .timezone("Asia/Jerusalem")
                .childName("Maya")
                .childAge(5)
                .childId(123L)
                .timeSlots(List.of(slot))
                .scheduledStart(now)
                .scheduledEnd(later)
                .scheduledTimeFormatted("Today at 5:00 PM")
                .streakCount(5)
                .longestStreak(10)
                .qualityTimeCount(25)
                .currentBelt(Belt.GREEN)
                .beltEarned(Belt.BLUE)
                .beltProgressPercentage(64)
                .qualityTimesUntilNextBelt(25)
                .activityIdeas(List.of(idea))
                .dashboardUrl("https://dadcoach.app/dashboard")
                .weeklyGoalMinutes(150)
                .weeklyCompletedMinutes(90)
                .validOptions(List.of("Option 1", "Option 2"))
                .completionNotes("Had fun playing!")
                .previousActivity("Reading together")
                .build();
            
            assertThat(context.getMessageType()).isEqualTo(MessageType.SCHEDULE_SLOTS);
            assertThat(context.getFatherName()).isEqualTo("David");
            assertThat(context.getLocale()).isEqualTo("he");
            assertThat(context.getTimezone()).isEqualTo("Asia/Jerusalem");
            assertThat(context.getChildName()).isEqualTo("Maya");
            assertThat(context.getChildAge()).isEqualTo(5);
            assertThat(context.getChildId()).isEqualTo(123L);
            assertThat(context.getTimeSlots()).hasSize(1);
            assertThat(context.getScheduledStart()).isEqualTo(now);
            assertThat(context.getScheduledEnd()).isEqualTo(later);
            assertThat(context.getScheduledTimeFormatted()).isEqualTo("Today at 5:00 PM");
            assertThat(context.getStreakCount()).isEqualTo(5);
            assertThat(context.getLongestStreak()).isEqualTo(10);
            assertThat(context.getQualityTimeCount()).isEqualTo(25);
            assertThat(context.getCurrentBelt()).isEqualTo(Belt.GREEN);
            assertThat(context.getBeltEarned()).isEqualTo(Belt.BLUE);
            assertThat(context.getBeltProgressPercentage()).isEqualTo(64);
            assertThat(context.getQualityTimesUntilNextBelt()).isEqualTo(25);
            assertThat(context.getActivityIdeas()).hasSize(1);
            assertThat(context.getDashboardUrl()).isEqualTo("https://dadcoach.app/dashboard");
            assertThat(context.getWeeklyGoalMinutes()).isEqualTo(150);
            assertThat(context.getWeeklyCompletedMinutes()).isEqualTo(90);
            assertThat(context.getValidOptions()).containsExactly("Option 1", "Option 2");
            assertThat(context.getCompletionNotes()).isEqualTo("Had fun playing!");
            assertThat(context.getPreviousActivity()).isEqualTo("Reading together");
        }
        
        @Test
        @DisplayName("should use default locale when not specified")
        void shouldUseDefaultLocaleWhenNotSpecified() {
            MessageContext context = MessageContext.builder()
                .fatherName("David")
                .build();
            
            assertThat(context.getLocale()).isEqualTo("en");
            assertThat(context.isEnglish()).isTrue();
            assertThat(context.isHebrew()).isFalse();
        }
        
        @Test
        @DisplayName("should use default timezone when not specified")
        void shouldUseDefaultTimezoneWhenNotSpecified() {
            MessageContext context = MessageContext.builder()
                .fatherName("David")
                .build();
            
            assertThat(context.getTimezone()).isEqualTo("Asia/Jerusalem");
        }
    }
    
    @Nested
    @DisplayName("toBuilder")
    class ToBuilderTests {
        
        @Test
        @DisplayName("should create copy with same values")
        void shouldCreateCopyWithSameValues() {
            MessageContext original = MessageContext.builder()
                .messageType(MessageType.WELCOME_GREETING)
                .fatherName("David")
                .childName("Maya")
                .locale("he")
                .build();
            
            MessageContext copy = original.toBuilder().build();
            
            assertThat(copy).isEqualTo(original);
            assertThat(copy.getFatherName()).isEqualTo("David");
            assertThat(copy.getChildName()).isEqualTo("Maya");
            assertThat(copy.getLocale()).isEqualTo("he");
        }
        
        @Test
        @DisplayName("should allow modifying values on copy")
        void shouldAllowModifyingValuesOnCopy() {
            MessageContext original = MessageContext.builder()
                .messageType(MessageType.WELCOME_GREETING)
                .fatherName("David")
                .locale("en")
                .build();
            
            MessageContext modified = original.toBuilder()
                .messageType(MessageType.SCHEDULE_SLOTS)
                .locale("he")
                .build();
            
            assertThat(original.getMessageType()).isEqualTo(MessageType.WELCOME_GREETING);
            assertThat(original.getLocale()).isEqualTo("en");
            assertThat(modified.getMessageType()).isEqualTo(MessageType.SCHEDULE_SLOTS);
            assertThat(modified.getLocale()).isEqualTo("he");
            assertThat(modified.getFatherName()).isEqualTo("David");
        }
    }
    
    @Nested
    @DisplayName("Locale Utilities")
    class LocaleUtilitiesTests {
        
        @Test
        @DisplayName("isHebrew should return true for Hebrew locale")
        void isHebrewShouldReturnTrueForHebrewLocale() {
            MessageContext context = MessageContext.builder()
                .locale("he")
                .build();
            
            assertThat(context.isHebrew()).isTrue();
            assertThat(context.isEnglish()).isFalse();
        }
        
        @Test
        @DisplayName("isEnglish should return true for English locale")
        void isEnglishShouldReturnTrueForEnglishLocale() {
            MessageContext context = MessageContext.builder()
                .locale("en")
                .build();
            
            assertThat(context.isEnglish()).isTrue();
            assertThat(context.isHebrew()).isFalse();
        }
    }
    
    @Nested
    @DisplayName("Timezone Utilities")
    class TimezoneUtilitiesTests {
        
        @Test
        @DisplayName("should return ZoneId for valid timezone")
        void shouldReturnZoneIdForValidTimezone() {
            MessageContext context = MessageContext.builder()
                .timezone("America/New_York")
                .build();
            
            assertThat(context.getTimezoneAsZoneId())
                .isEqualTo(ZoneId.of("America/New_York"));
        }
        
        @Test
        @DisplayName("should return default ZoneId for invalid timezone")
        void shouldReturnDefaultZoneIdForInvalidTimezone() {
            MessageContext context = MessageContext.builder()
                .timezone("Invalid/Timezone")
                .build();
            
            assertThat(context.getTimezoneAsZoneId())
                .isEqualTo(ZoneId.of("Asia/Jerusalem"));
        }
    }
    
    @Nested
    @DisplayName("State Utilities")
    class StateUtilitiesTests {
        
        @Test
        @DisplayName("hasTimeSlots should return true when slots exist")
        void hasTimeSlotsShouldReturnTrueWhenSlotsExist() {
            Instant now = Instant.now();
            AvailableSlot slot = AvailableSlot.ofDuration(now, 30);
            
            MessageContext context = MessageContext.builder()
                .timeSlots(List.of(slot))
                .build();
            
            assertThat(context.hasTimeSlots()).isTrue();
        }
        
        @Test
        @DisplayName("hasTimeSlots should return false when no slots")
        void hasTimeSlotsShouldReturnFalseWhenNoSlots() {
            MessageContext context = MessageContext.builder().build();
            
            assertThat(context.hasTimeSlots()).isFalse();
        }
        
        @Test
        @DisplayName("hasBeltEarned should return true when belt earned")
        void hasBeltEarnedShouldReturnTrueWhenBeltEarned() {
            MessageContext context = MessageContext.builder()
                .beltEarned(Belt.YELLOW)
                .build();
            
            assertThat(context.hasBeltEarned()).isTrue();
        }
        
        @Test
        @DisplayName("hasBeltEarned should return false when no belt earned")
        void hasBeltEarnedShouldReturnFalseWhenNoBeltEarned() {
            MessageContext context = MessageContext.builder().build();
            
            assertThat(context.hasBeltEarned()).isFalse();
        }
        
        @Test
        @DisplayName("hasActivityIdeas should return true when ideas exist")
        void hasActivityIdeasShouldReturnTrueWhenIdeasExist() {
            MessageContext.ActivityIdea idea = new MessageContext.ActivityIdea(
                "Build", "Build a tower", 30, true
            );
            MessageContext context = MessageContext.builder()
                .activityIdeas(List.of(idea))
                .build();
            
            assertThat(context.hasActivityIdeas()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Default Value Utilities")
    class DefaultValueUtilitiesTests {
        
        @Test
        @DisplayName("getFatherNameOrDefault should return name when set")
        void getFatherNameOrDefaultShouldReturnNameWhenSet() {
            MessageContext context = MessageContext.builder()
                .fatherName("David")
                .build();
            
            assertThat(context.getFatherNameOrDefault("Unknown")).isEqualTo("David");
        }
        
        @Test
        @DisplayName("getFatherNameOrDefault should return default when not set")
        void getFatherNameOrDefaultShouldReturnDefaultWhenNotSet() {
            MessageContext context = MessageContext.builder().build();
            
            assertThat(context.getFatherNameOrDefault("Unknown")).isEqualTo("Unknown");
        }
        
        @Test
        @DisplayName("getChildNameOrDefault should return name when set")
        void getChildNameOrDefaultShouldReturnNameWhenSet() {
            MessageContext context = MessageContext.builder()
                .childName("Maya")
                .build();
            
            assertThat(context.getChildNameOrDefault("Your child")).isEqualTo("Maya");
        }
        
        @Test
        @DisplayName("getChildNameOrDefault should return default when not set")
        void getChildNameOrDefaultShouldReturnDefaultWhenNotSet() {
            MessageContext context = MessageContext.builder().build();
            
            assertThat(context.getChildNameOrDefault("Your child")).isEqualTo("Your child");
        }
    }
    
    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {
        
        @Test
        @DisplayName("timeSlots should be immutable")
        void timeSlotsShouldBeImmutable() {
            Instant now = Instant.now();
            AvailableSlot slot = AvailableSlot.ofDuration(now, 30);
            
            MessageContext context = MessageContext.builder()
                .timeSlots(List.of(slot))
                .build();
            
            assertThatThrownBy(() -> context.getTimeSlots().add(slot))
                .isInstanceOf(UnsupportedOperationException.class);
        }
        
        @Test
        @DisplayName("validOptions should be immutable")
        void validOptionsShouldBeImmutable() {
            MessageContext context = MessageContext.builder()
                .validOptions(List.of("Option 1"))
                .build();
            
            assertThatThrownBy(() -> context.getValidOptions().add("Option 2"))
                .isInstanceOf(UnsupportedOperationException.class);
        }
        
        @Test
        @DisplayName("activityIdeas should be immutable")
        void activityIdeasShouldBeImmutable() {
            MessageContext.ActivityIdea idea = new MessageContext.ActivityIdea(
                "Build", "Build a tower", 30, true
            );
            MessageContext context = MessageContext.builder()
                .activityIdeas(List.of(idea))
                .build();
            
            assertThatThrownBy(() -> context.getActivityIdeas().add(idea))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
    
    @Nested
    @DisplayName("ActivityIdea Record")
    class ActivityIdeaTests {
        
        @Test
        @DisplayName("should create valid activity idea")
        void shouldCreateValidActivityIdea() {
            MessageContext.ActivityIdea idea = new MessageContext.ActivityIdea(
                "Building with LEGO",
                "Build a tower together using colorful blocks.",
                30,
                true
            );
            
            assertThat(idea.title()).isEqualTo("Building with LEGO");
            assertThat(idea.description()).isEqualTo("Build a tower together using colorful blocks.");
            assertThat(idea.durationMinutes()).isEqualTo(30);
            assertThat(idea.indoor()).isTrue();
            assertThat(idea.isOutdoor()).isFalse();
        }
        
        @Test
        @DisplayName("should identify outdoor activities")
        void shouldIdentifyOutdoorActivities() {
            MessageContext.ActivityIdea idea = new MessageContext.ActivityIdea(
                "Nature Walk",
                "Explore the park together.",
                45,
                false
            );
            
            assertThat(idea.indoor()).isFalse();
            assertThat(idea.isOutdoor()).isTrue();
        }
        
        @Test
        @DisplayName("should reject null title")
        void shouldRejectNullTitle() {
            assertThatThrownBy(() -> new MessageContext.ActivityIdea(
                null, "Description", 30, true
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title");
        }
        
        @Test
        @DisplayName("should reject blank title")
        void shouldRejectBlankTitle() {
            assertThatThrownBy(() -> new MessageContext.ActivityIdea(
                "  ", "Description", 30, true
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title");
        }
        
        @Test
        @DisplayName("should reject null description")
        void shouldRejectNullDescription() {
            assertThatThrownBy(() -> new MessageContext.ActivityIdea(
                "Title", null, 30, true
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("description");
        }
        
        @Test
        @DisplayName("should reject non-positive duration")
        void shouldRejectNonPositiveDuration() {
            assertThatThrownBy(() -> new MessageContext.ActivityIdea(
                "Title", "Description", 0, true
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("durationMinutes");
            
            assertThatThrownBy(() -> new MessageContext.ActivityIdea(
                "Title", "Description", -1, true
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("durationMinutes");
        }
    }
    
    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCodeTests {
        
        @Test
        @DisplayName("equal contexts should have same hashCode")
        void equalContextsShouldHaveSameHashCode() {
            MessageContext context1 = MessageContext.builder()
                .messageType(MessageType.WELCOME_GREETING)
                .fatherName("David")
                .locale("he")
                .build();
            
            MessageContext context2 = MessageContext.builder()
                .messageType(MessageType.WELCOME_GREETING)
                .fatherName("David")
                .locale("he")
                .build();
            
            assertThat(context1).isEqualTo(context2);
            assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
        }
        
        @Test
        @DisplayName("different contexts should not be equal")
        void differentContextsShouldNotBeEqual() {
            MessageContext context1 = MessageContext.builder()
                .fatherName("David")
                .build();
            
            MessageContext context2 = MessageContext.builder()
                .fatherName("Michael")
                .build();
            
            assertThat(context1).isNotEqualTo(context2);
        }
    }
    
    @Nested
    @DisplayName("ToString")
    class ToStringTests {
        
        @Test
        @DisplayName("toString should contain key fields")
        void toStringShouldContainKeyFields() {
            MessageContext context = MessageContext.builder()
                .messageType(MessageType.WELCOME_GREETING)
                .fatherName("David")
                .locale("he")
                .timezone("Asia/Jerusalem")
                .build();
            
            String str = context.toString();
            
            assertThat(str).contains("MessageContext");
            assertThat(str).contains("WELCOME_GREETING");
            assertThat(str).contains("David");
            assertThat(str).contains("he");
            assertThat(str).contains("Asia/Jerusalem");
        }
    }
}
