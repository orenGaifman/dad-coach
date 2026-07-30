package com.dadcoach.api.admin;

import com.dadcoach.api.pagination.CursorPageResponse;
import org.springframework.stereotype.Service;

/**
 * Stub implementation of AdminSearchService for development.
 */
@Service
public class AdminSearchServiceImpl implements AdminSearchService {

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
}
