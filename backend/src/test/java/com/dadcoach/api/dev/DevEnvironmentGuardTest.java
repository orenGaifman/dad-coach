package com.dadcoach.api.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

/**
 * Unit tests for {@link DevEnvironmentGuard}.
 *
 * <p>These tests verify the environment detection logic according to Requirements 12.1-12.5:</p>
 * <ul>
 *   <li>12.1: Use DevEnvironmentGuard to determine if dev endpoints are allowed</li>
 *   <li>12.2: Allow access when dadcoach.dev.enabled is explicitly true</li>
 *   <li>12.3: Block access when dadcoach.dev.enabled is explicitly false</li>
 *   <li>12.4: Block access when profile is prod/production (when property not set)</li>
 *   <li>12.5: Block access on any exception as security precaution</li>
 * </ul>
 */
class DevEnvironmentGuardTest {

    @Nested
    @DisplayName("isDevAllowed()")
    class IsDevAllowed {

        @Nested
        @DisplayName("Explicit configuration (dadcoach.dev.enabled)")
        class ExplicitConfiguration {

            @Test
            @DisplayName("should allow access when devEnabled is explicitly true")
            void shouldAllowAccessWhenDevEnabledIsTrue() {
                // Validates: Requirement 12.2
                Environment mockEnvironment = mock(Environment.class);
                DevEnvironmentGuard guard = new DevEnvironmentGuard(true, mockEnvironment);

                assertThat(guard.isDevAllowed()).isTrue();
            }

            @Test
            @DisplayName("should block access when devEnabled is explicitly false")
            void shouldBlockAccessWhenDevEnabledIsFalse() {
                // Validates: Requirement 12.3
                Environment mockEnvironment = mock(Environment.class);
                DevEnvironmentGuard guard = new DevEnvironmentGuard(false, mockEnvironment);

                assertThat(guard.isDevAllowed()).isFalse();
            }

            @Test
            @DisplayName("should allow access when devEnabled is true regardless of profile")
            void shouldAllowAccessWhenDevEnabledTrueIgnoringProfile() {
                // Validates: Requirement 12.2 - explicit config takes precedence
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"prod"});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(true, mockEnvironment);

                assertThat(guard.isDevAllowed()).isTrue();
            }

            @Test
            @DisplayName("should block access when devEnabled is false regardless of profile")
            void shouldBlockAccessWhenDevEnabledFalseIgnoringProfile() {
                // Validates: Requirement 12.3 - explicit config takes precedence
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"dev"});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(false, mockEnvironment);

                assertThat(guard.isDevAllowed()).isFalse();
            }
        }

        @Nested
        @DisplayName("Spring profile fallback")
        class SpringProfileFallback {

            @Test
            @DisplayName("should block access for 'prod' profile")
            void shouldBlockAccessForProdProfile() {
                // Validates: Requirement 12.4, 5.1
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"prod"});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

                assertThat(guard.isDevAllowed()).isFalse();
            }

            @Test
            @DisplayName("should block access for 'production' profile")
            void shouldBlockAccessForProductionProfile() {
                // Validates: Requirement 12.4, 5.1
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"production"});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

                assertThat(guard.isDevAllowed()).isFalse();
            }

            @Test
            @DisplayName("should block access for 'PROD' profile (case-insensitive)")
            void shouldBlockAccessForProdProfileCaseInsensitive() {
                // Validates: Requirement 5.1 - case-insensitive check
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"PROD"});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

                assertThat(guard.isDevAllowed()).isFalse();
            }

            @Test
            @DisplayName("should block access for 'Production' profile (case-insensitive)")
            void shouldBlockAccessForProductionProfileCaseInsensitive() {
                // Validates: Requirement 5.1 - case-insensitive check
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"Production"});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

                assertThat(guard.isDevAllowed()).isFalse();
            }

            @Test
            @DisplayName("should allow access for 'dev' profile")
            void shouldAllowAccessForDevProfile() {
                // Validates: Requirement 5.2
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"dev"});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

                assertThat(guard.isDevAllowed()).isTrue();
            }

            @Test
            @DisplayName("should allow access for 'local' profile")
            void shouldAllowAccessForLocalProfile() {
                // Validates: Requirement 5.2
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"local"});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

                assertThat(guard.isDevAllowed()).isTrue();
            }

            @Test
            @DisplayName("should allow access for 'staging' profile")
            void shouldAllowAccessForStagingProfile() {
                // Validates: Requirement 5.2
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"staging"});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

                assertThat(guard.isDevAllowed()).isTrue();
            }

            @Test
            @DisplayName("should allow access for 'test' profile")
            void shouldAllowAccessForTestProfile() {
                // Validates: Requirement 5.2
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"test"});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

                assertThat(guard.isDevAllowed()).isTrue();
            }

            @Test
            @DisplayName("should allow access for 'qa' profile")
            void shouldAllowAccessForQaProfile() {
                // Validates: Requirement 5.2
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"qa"});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

                assertThat(guard.isDevAllowed()).isTrue();
            }

            @Test
            @DisplayName("should allow access when no profiles are active")
            void shouldAllowAccessWhenNoProfilesActive() {
                // Validates: Requirement 5.2 - default behavior is to allow
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

                assertThat(guard.isDevAllowed()).isTrue();
            }

            @Test
            @DisplayName("should block access when any profile is 'prod'")
            void shouldBlockAccessWhenAnyProfileIsProd() {
                // Validates: Requirement 5.1 - check all active profiles
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"dev", "prod"});
                DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

                assertThat(guard.isDevAllowed()).isFalse();
            }
        }

        @Nested
        @DisplayName("Exception handling (security precaution)")
        class ExceptionHandling {

            @Test
            @DisplayName("should block access when getActiveProfiles throws exception")
            void shouldBlockAccessOnException() {
                // Validates: Requirement 12.5
                Environment mockEnvironment = mock(Environment.class);
                when(mockEnvironment.getActiveProfiles()).thenThrow(new RuntimeException("Failure"));
                DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

                assertThat(guard.isDevAllowed()).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("requireDevAccess()")
    class RequireDevAccess {

        @Test
        @DisplayName("should not throw when dev access is allowed")
        void shouldNotThrowWhenDevAccessAllowed() {
            Environment mockEnvironment = mock(Environment.class);
            when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"dev"});
            DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

            // Should not throw
            guard.requireDevAccess();
        }

        @Test
        @DisplayName("should throw DevEndpointsDisabledException when access denied")
        void shouldThrowWhenDevAccessDenied() {
            // Validates: Requirement 5.1
            Environment mockEnvironment = mock(Environment.class);
            when(mockEnvironment.getActiveProfiles()).thenReturn(new String[]{"prod"});
            DevEnvironmentGuard guard = new DevEnvironmentGuard(null, mockEnvironment);

            assertThatThrownBy(guard::requireDevAccess)
                    .isInstanceOf(DevEndpointsDisabledException.class)
                    .hasMessage("Dev endpoints disabled in production");
        }

        @Test
        @DisplayName("should throw when devEnabled is explicitly false")
        void shouldThrowWhenDevEnabledFalse() {
            // Validates: Requirement 12.3
            Environment mockEnvironment = mock(Environment.class);
            DevEnvironmentGuard guard = new DevEnvironmentGuard(false, mockEnvironment);

            assertThatThrownBy(guard::requireDevAccess)
                    .isInstanceOf(DevEndpointsDisabledException.class);
        }
    }
}
