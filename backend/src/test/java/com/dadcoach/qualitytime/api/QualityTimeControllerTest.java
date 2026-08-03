package com.dadcoach.qualitytime.api;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.ActorType;
import com.dadcoach.api.error.ResourceNotFoundException;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.systemstate.AvailableSlot;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.workflow.dto.AvailableSlotsDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QualityTimeController.
 * 
 * Validates: Requirements 2.3, 14.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QualityTimeController")
class QualityTimeControllerTest {

    @Mock
    private QualityTimeService qualityTimeService;

    @Mock
    private QualityTimeRepository qualityTimeRepository;

    @Mock
    private SystemStateLoader systemStateLoader;

    @Mock
    private FatherRepository fatherRepository;

    @Mock
    private ChildRepository childRepository;

    private QualityTimeController controller;

    private UUID fatherId;
    private long numericFatherId;
    private Father father;
    private ActorContext actorContext;

    @BeforeEach
    void setUp() {
        controller = new QualityTimeController(
                qualityTimeService,
                qualityTimeRepository,
                systemStateLoader,
                fatherRepository,
                childRepository);
        
        numericFatherId = 12345L;
        fatherId = new UUID(0, numericFatherId);
        
        father = new Father("+1234567890");
        father.setId(numericFatherId);
        father.setDisplayName("Test Dad");
        father.setTimezone("America/New_York");
        
        actorContext = new ActorContext(ActorType.FATHER, fatherId);
    }

    @Nested
    @DisplayName("getAvailableSlots")
    class GetAvailableSlotsTest {

        @Test
        @DisplayName("returns 200 OK with available slots when calendar is connected")
        void returnsAvailableSlotsWhenCalendarConnected() {
            // Given - configure Google Calendar properly
            father.setGoogleCalendarEnabled(true);
            father.setGoogleRefreshToken("test-refresh-token");
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));

            Instant now = Instant.now();
            List<AvailableSlot> slots = List.of(
                    AvailableSlot.of(now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(3))),
                    AvailableSlot.of(now.plus(Duration.ofHours(5)), now.plus(Duration.ofHours(7)))
            );
            when(systemStateLoader.loadAvailableSlots(fatherId, 7)).thenReturn(slots);

            // When
            ResponseEntity<AvailableSlotsDto> response = controller.getAvailableSlots(actorContext, 7, 30);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().calendarConnected()).isTrue();
            assertThat(response.getBody().timezone()).isEqualTo("America/New_York");
            assertThat(response.getBody().slots()).hasSize(2);
        }

        @Test
        @DisplayName("returns empty slots with calendar_connected=false when calendar not connected")
        void returnsEmptySlotsWhenCalendarNotConnected() {
            // Given
            // Father without calendar configured (no google tokens)
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));

            // When
            ResponseEntity<AvailableSlotsDto> response = controller.getAvailableSlots(actorContext, 7, 30);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().calendarConnected()).isFalse();
            assertThat(response.getBody().timezone()).isEqualTo("America/New_York");
            assertThat(response.getBody().slots()).isEmpty();
            
            // Should not call systemStateLoader when calendar is not connected
            verify(systemStateLoader, never()).loadAvailableSlots(any(), anyInt());
        }

        @Test
        @DisplayName("uses default timezone when father has no timezone configured")
        void usesDefaultTimezoneWhenNotConfigured() {
            // Given
            father.setTimezone(null);
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));

            // When
            ResponseEntity<AvailableSlotsDto> response = controller.getAvailableSlots(actorContext, 7, 30);

            // Then
            assertThat(response.getBody().timezone()).isEqualTo("Asia/Jerusalem");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when father not found")
        void throwsWhenFatherNotFound() {
            // Given
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> controller.getAvailableSlots(actorContext, 7, 30))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Father");
        }

        @Nested
        @DisplayName("parameter validation")
        class ParameterValidationTest {

            @BeforeEach
            void setUpConnectedCalendar() {
                father.setGoogleCalendarEnabled(true);
                father.setGoogleRefreshToken("test-refresh-token");
                when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));
                when(systemStateLoader.loadAvailableSlots(eq(fatherId), anyInt())).thenReturn(List.of());
            }

            @ParameterizedTest(name = "days_ahead={0} coerced to 7")
            @ValueSource(ints = {0, -1, -100})
            @DisplayName("coerces invalid days_ahead to default value")
            void coercesInvalidDaysAheadToDefault(int invalidDaysAhead) {
                controller.getAvailableSlots(actorContext, invalidDaysAhead, 30);

                verify(systemStateLoader).loadAvailableSlots(fatherId, 7);
            }

            @ParameterizedTest(name = "days_ahead={0} coerced to 14")
            @ValueSource(ints = {15, 30, 100})
            @DisplayName("coerces days_ahead above max to max value")
            void coercesDaysAheadAboveMaxToMax(int tooHighDaysAhead) {
                controller.getAvailableSlots(actorContext, tooHighDaysAhead, 30);

                verify(systemStateLoader).loadAvailableSlots(fatherId, 14);
            }

            @Test
            @DisplayName("filters slots by minimum duration")
            void filtersByMinimumDuration() {
                // Given
                Instant now = Instant.now();
                List<AvailableSlot> slots = List.of(
                        AvailableSlot.ofDuration(now.plus(Duration.ofHours(1)), 60),  // 60 min - should pass
                        AvailableSlot.ofDuration(now.plus(Duration.ofHours(3)), 30),  // 30 min - should not pass for 45 min filter
                        AvailableSlot.ofDuration(now.plus(Duration.ofHours(5)), 90)   // 90 min - should pass
                );
                when(systemStateLoader.loadAvailableSlots(fatherId, 7)).thenReturn(slots);

                // When
                ResponseEntity<AvailableSlotsDto> response = controller.getAvailableSlots(actorContext, 7, 45);

                // Then
                assertThat(response.getBody().slots()).hasSize(2);
                assertThat(response.getBody().slots().get(0).durationMinutes()).isEqualTo(60);
                assertThat(response.getBody().slots().get(1).durationMinutes()).isEqualTo(90);
            }

            @ParameterizedTest(name = "min_duration_minutes={0} coerced to 30")
            @ValueSource(ints = {0, 10, 29})
            @DisplayName("coerces min_duration below minimum to minimum")
            void coercesMinDurationBelowMinimum(int lowMinDuration) {
                // Given
                Instant now = Instant.now();
                List<AvailableSlot> slots = List.of(
                        AvailableSlot.ofDuration(now.plus(Duration.ofHours(1)), 30)
                );
                when(systemStateLoader.loadAvailableSlots(fatherId, 7)).thenReturn(slots);

                // When
                ResponseEntity<AvailableSlotsDto> response = controller.getAvailableSlots(actorContext, 7, lowMinDuration);

                // Then
                // With min=30, a 30-min slot should be included
                assertThat(response.getBody().slots()).hasSize(1);
            }
        }

        @Test
        @DisplayName("maps AvailableSlot to AvailableSlotDto correctly")
        void mapsSlotsToDtosCorrectly() {
            // Given - configure Google Calendar properly
            father.setGoogleCalendarEnabled(true);
            father.setGoogleRefreshToken("test-refresh-token");
            when(fatherRepository.findById(numericFatherId)).thenReturn(Optional.of(father));

            Instant startTime = Instant.parse("2024-01-15T17:00:00Z");
            Instant endTime = Instant.parse("2024-01-15T19:00:00Z");
            List<AvailableSlot> slots = List.of(AvailableSlot.of(startTime, endTime));
            when(systemStateLoader.loadAvailableSlots(fatherId, 7)).thenReturn(slots);

            // When
            ResponseEntity<AvailableSlotsDto> response = controller.getAvailableSlots(actorContext, 7, 30);

            // Then
            assertThat(response.getBody().slots()).hasSize(1);
            var slotDto = response.getBody().slots().get(0);
            assertThat(slotDto.startTime()).isEqualTo(startTime);
            assertThat(slotDto.endTime()).isEqualTo(endTime);
            assertThat(slotDto.durationMinutes()).isEqualTo(120);
        }
    }
}
