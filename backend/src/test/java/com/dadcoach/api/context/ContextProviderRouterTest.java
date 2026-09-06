package com.dadcoach.api.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.weeklygoal.WeeklyGoalService;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ContextProviderRouter.
 * Verifies provider registration and routing works correctly.
 */
@ExtendWith(MockitoExtension.class)
class ContextProviderRouterTest {

    @Mock
    private FatherRepository fatherRepository;
    @Mock
    private ChildRepository childRepository;
    @Mock
    private QualityTimeRepository qualityTimeRepository;
    @Mock
    private WeeklyGoalService weeklyGoalService;
    @Mock
    private SystemStateLoader systemStateLoader;

    @InjectMocks
    private ContextProviderRouter router;

    private static final Long TEST_FATHER_ID = 1L;

    @Test
    @DisplayName("Should return all available context providers after initialization")
    void shouldReturnAllAvailableProvidersAfterInit() {
        router.initializeHandlers();
        
        Set<String> providers = router.getAvailableProviders();
        
        assertThat(providers).containsExactlyInAnyOrder(
                "family_context",
                "calendar_context",
                "quality_time_context"
        );
    }

    @Test
    @DisplayName("Should return provider not found for unknown provider")
    void shouldReturnProviderNotFound() {
        router.initializeHandlers();
        
        ContextProviderRequest request = new ContextProviderRequest(TEST_FATHER_ID, Map.of());
        
        ContextProviderResponse response = router.dispatch("unknown_provider", request);
        
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("PROVIDER_NOT_FOUND");
    }
}
