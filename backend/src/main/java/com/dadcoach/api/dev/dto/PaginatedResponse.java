package com.dadcoach.api.dev.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Generic paginated response wrapper for Dev API list endpoints.
 * Provides pagination metadata and hypermedia links.
 *
 * @param <T> The type of items in the response
 * @param items The list of items on this page
 * @param page The current page number (zero-indexed)
 * @param pageSize The number of items per page
 * @param totalItems The total number of items across all pages
 * @param totalPages The total number of pages
 * @param links Hypermedia links for navigation (self, next, prev, first, last)
 */
public record PaginatedResponse<T>(
    List<T> items,
    
    int page,
    
    @JsonProperty("page_size")
    int pageSize,
    
    @JsonProperty("total_items")
    long totalItems,
    
    @JsonProperty("total_pages")
    int totalPages,
    
    @JsonProperty("_links")
    Map<String, String> links
) {
    
    /**
     * Creates a PaginatedResponse with the given items and pagination info.
     * The items list is made unmodifiable.
     *
     * @param items The items on this page
     * @param page The current page number (zero-indexed)
     * @param pageSize The number of items per page
     * @param totalItems The total number of items across all pages
     * @param totalPages The total number of pages
     * @param links The hypermedia links for navigation
     */
    public PaginatedResponse {
        items = items != null ? Collections.unmodifiableList(items) : Collections.emptyList();
        links = links != null ? Collections.unmodifiableMap(links) : Collections.emptyMap();
    }
    
    /**
     * Creates an empty paginated response.
     *
     * @param <T> The item type
     * @param page The current page number
     * @param pageSize The page size
     * @return An empty paginated response
     */
    public static <T> PaginatedResponse<T> empty(int page, int pageSize) {
        return new PaginatedResponse<>(
            Collections.emptyList(),
            page,
            pageSize,
            0L,
            0,
            Collections.emptyMap()
        );
    }
    
    /**
     * Creates a PaginatedResponse from a list of items and pagination parameters.
     *
     * @param <T> The item type
     * @param items The items on this page
     * @param page The current page number (zero-indexed)
     * @param pageSize The number of items per page
     * @param totalItems The total number of items across all pages
     * @param baseUrl The base URL for generating navigation links
     * @return A paginated response with generated navigation links
     */
    public static <T> PaginatedResponse<T> of(
            List<T> items, 
            int page, 
            int pageSize, 
            long totalItems, 
            String baseUrl) {
        
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalItems / pageSize) : 0;
        
        Map<String, String> links = new java.util.LinkedHashMap<>();
        links.put("self", buildUrl(baseUrl, page, pageSize));
        
        if (page > 0) {
            links.put("first", buildUrl(baseUrl, 0, pageSize));
            links.put("prev", buildUrl(baseUrl, page - 1, pageSize));
        }
        
        if (page < totalPages - 1) {
            links.put("next", buildUrl(baseUrl, page + 1, pageSize));
            links.put("last", buildUrl(baseUrl, totalPages - 1, pageSize));
        }
        
        return new PaginatedResponse<>(items, page, pageSize, totalItems, totalPages, links);
    }
    
    private static String buildUrl(String baseUrl, int page, int pageSize) {
        return String.format("%s?page=%d&page_size=%d", baseUrl, page, pageSize);
    }
}
