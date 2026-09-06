package com.dadcoach.api.context;

/**
 * Functional interface for context provider handlers.
 * 
 * <p>Each context provider implements this interface to provide its specific
 * context data aggregation logic.</p>
 * 
 * @see ContextProviderRouter
 */
@FunctionalInterface
public interface ContextProviderHandler {
    
    /**
     * Loads context data for the given request.
     *
     * @param request the context provider request containing userId and config
     * @return the context provider response with aggregated data
     */
    ContextProviderResponse loadContext(ContextProviderRequest request);
}
