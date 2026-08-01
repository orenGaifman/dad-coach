package com.dadcoach.api.config;

import com.dadcoach.api.admin.AdminSearchResultDto;
import com.dadcoach.api.admin.AdminSearchService;
import com.dadcoach.api.admin.AggregatedAnalyticsDto;
import com.dadcoach.api.admin.EngagementMetricsDto;
import com.dadcoach.api.child.ChildCreateRequest;
import com.dadcoach.api.child.ChildResponseDto;
import com.dadcoach.api.child.ChildService;
import com.dadcoach.api.conversation.ConversationResponseDto;
import com.dadcoach.api.conversation.ConversationService;
import com.dadcoach.api.father.AdminFatherDetailDto;
import com.dadcoach.api.father.AdminFatherService;
import com.dadcoach.api.father.AdminFatherSummaryDto;
import com.dadcoach.api.health.AiProviderHealthIndicator;
import com.dadcoach.api.health.WhatsAppHealthIndicator;
import com.dadcoach.api.memory.AdminMemoryController;
import com.dadcoach.api.memory.AdminMemoryDto;
import com.dadcoach.api.memory.AdminMemoryService;
import com.dadcoach.api.memory.MemoryAuditEntryDto;
import com.dadcoach.api.memory.MemoryResponseDto;
import com.dadcoach.api.memory.MemoryService;
import com.dadcoach.api.pagination.CursorPageResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

/**
 * Provides stub/no-op implementations for API service interfaces that don't yet
 * have domain-layer implementations. This allows the application to start and
 * API endpoints to return structured "not implemented" responses.
 * <p>
 * Each stub is registered only if no other bean of that type exists
 * ({@code @ConditionalOnMissingBean}), so domain implementations will take
 * precedence once they are available.
 * <p>
 * Delete this class once all real implementations are in place.
 */
@Configuration
public class ApiServiceStubConfig {

    @Bean("apiChildService")
    @ConditionalOnMissingBean(com.dadcoach.api.child.ChildService.class)
    public ChildService childService() {
        return new ChildService() {
            @Override
            public ChildResponseDto createChild(UUID fatherId, ChildCreateRequest request) {
                throw new UnsupportedOperationException("Child service not yet implemented");
            }

            @Override
            public List<ChildResponseDto> listChildren(UUID fatherId) {
                return List.of();
            }

            @Override
            public Optional<ChildResponseDto> findById(UUID childId) {
                return Optional.empty();
            }

            @Override
            public ChildResponseDto updateChild(UUID childId, ChildCreateRequest request) {
                throw new UnsupportedOperationException("Child service not yet implemented");
            }

            @Override
            public void deleteChild(UUID childId) {
                throw new UnsupportedOperationException("Child service not yet implemented");
            }

            @Override
            public int countActiveChildren(UUID fatherId) {
                return 0;
            }

            @Override
            public Optional<UUID> getOwnerFatherId(UUID childId) {
                return Optional.empty();
            }
        };
    }

    @Bean("apiConversationService")
    @ConditionalOnMissingBean(com.dadcoach.api.conversation.ConversationService.class)
    public ConversationService conversationService() {
        return new ConversationService() {
            @Override
            public ConversationPage listConversations(UUID fatherId, String cursor, int pageSize) {
                return new ConversationPage(List.of(), null, false);
            }

            @Override
            public Optional<ConversationResponseDto> getConversationWithMessages(UUID conversationId) {
                return Optional.empty();
            }

            @Override
            public Optional<UUID> getConversationOwnerId(UUID conversationId) {
                return Optional.empty();
            }
        };
    }

    @Bean("apiMemoryService")
    @ConditionalOnMissingBean(com.dadcoach.api.memory.MemoryService.class)
    public MemoryService memoryService() {
        return new MemoryService() {
            @Override
            public MemoryPage listActiveMemories(UUID fatherId, String cursor, int pageSize) {
                return new MemoryPage(List.of(), null, false);
            }

            @Override
            public Optional<MemoryResponseDto> getMemory(UUID memoryId) {
                return Optional.empty();
            }

            @Override
            public Optional<UUID> getMemoryOwnerId(UUID memoryId) {
                return Optional.empty();
            }

            @Override
            public void deleteMemory(UUID memoryId) {
                throw new UnsupportedOperationException("Memory service not yet implemented");
            }
        };
    }

    @Bean("apiAdminFatherService")
    @ConditionalOnMissingBean(com.dadcoach.api.father.AdminFatherService.class)
    public AdminFatherService adminFatherService() {
        return new AdminFatherService() {
            @Override
            public CursorPageResponse<AdminFatherSummaryDto> listFathers(
                    String query, String status, String phase, String cursor, int pageSize) {
                return CursorPageResponse.empty();
            }

            @Override
            public Optional<AdminFatherDetailDto> getFatherDetail(UUID fatherId) {
                return Optional.empty();
            }

            @Override
            public void deleteFather(Long fatherId) {
                throw new UnsupportedOperationException("Admin father service not yet implemented");
            }
        };
    }

    @Bean("apiAdminMemoryService")
    @ConditionalOnMissingBean(com.dadcoach.api.memory.AdminMemoryService.class)
    public AdminMemoryService adminMemoryService() {
        return new AdminMemoryService() {
            @Override
            public CursorPageResponse<AdminMemoryDto> listAllMemories(
                    UUID fatherId, String state, String cursor, int pageSize) {
                return CursorPageResponse.empty();
            }

            @Override
            public Optional<AdminMemoryDto> getMemoryDetail(UUID memoryId) {
                return Optional.empty();
            }

            @Override
            public List<MemoryAuditEntryDto> getMemoryAuditHistory(UUID memoryId) {
                return List.of();
            }
        };
    }

    @Bean("apiAdminSearchService")
    @ConditionalOnMissingBean(com.dadcoach.api.admin.AdminSearchService.class)
    public AdminSearchService adminSearchService() {
        return new AdminSearchService() {
            @Override
            public CursorPageResponse<AdminSearchResultDto> searchFathers(
                    String query, String status, String phase, String cursor, int pageSize) {
                return CursorPageResponse.empty();
            }

            @Override
            public AggregatedAnalyticsDto getAggregatedAnalytics(String status, String phase) {
                return new AggregatedAnalyticsDto();
            }

            @Override
            public EngagementMetricsDto getEngagementMetrics() {
                return new EngagementMetricsDto();
            }
        };
    }

    @Bean("apiAiProviderHealthIndicator")
    @ConditionalOnMissingBean(AiProviderHealthIndicator.class)
    public AiProviderHealthIndicator aiProviderHealthIndicator() {
        return new AiProviderHealthIndicator() {
            @Override
            public String checkHealth() {
                return "UNKNOWN";
            }

            @Override
            public Map<String, Object> getDetails() {
                return Map.of("status", "stub", "message", "AI provider health check not yet implemented");
            }
        };
    }

    @Bean("apiWhatsAppHealthIndicator")
    @ConditionalOnMissingBean(WhatsAppHealthIndicator.class)
    public WhatsAppHealthIndicator whatsAppHealthIndicator() {
        return new WhatsAppHealthIndicator() {
            @Override
            public String checkHealth() {
                return "UNKNOWN";
            }

            @Override
            public Map<String, Object> getDetails() {
                return Map.of("status", "stub", "message", "WhatsApp health check not yet implemented");
            }
        };
    }
}
