/**
 * System state management for the Read Before Write pattern.
 * 
 * <p>This package contains the {@link com.dadcoach.systemstate.SystemState} record
 * and related components for loading and caching complete system state before
 * processing any request in the deterministic workflow engine.</p>
 * 
 * <p>The Read Before Write principle ensures:</p>
 * <ul>
 *   <li>The system never asks for information it already has</li>
 *   <li>The system never suggests times that conflict with the calendar</li>
 *   <li>All state is synchronized at the beginning of each request cycle</li>
 * </ul>
 * 
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link com.dadcoach.systemstate.SystemState} - Immutable record holding complete state snapshot</li>
 *   <li>{@link com.dadcoach.systemstate.SystemStateLoader} - Interface for loading state from data sources</li>
 *   <li>{@link com.dadcoach.systemstate.SystemStateCache} - Thread-local cache for request-scoped state caching</li>
 *   <li>{@link com.dadcoach.systemstate.SystemStateCacheFilter} - Servlet filter ensuring cache cleanup after requests</li>
 *   <li>{@link com.dadcoach.systemstate.AvailableSlot} - Available time slot for Quality Time scheduling</li>
 * </ul>
 * 
 * <h2>Request-Scoped Caching (Requirement 2.4)</h2>
 * <p>The {@link com.dadcoach.systemstate.SystemStateCache} caches the loaded 
 * {@link com.dadcoach.systemstate.SystemState} for the duration of a single request.
 * This avoids redundant database and external API calls within the same request cycle.
 * The {@link com.dadcoach.systemstate.SystemStateCacheFilter} ensures the cache is
 * always cleared after request completion to prevent memory leaks.</p>
 * 
 * @see com.dadcoach.systemstate.SystemState
 * @see com.dadcoach.systemstate.SystemStateLoader
 * @see com.dadcoach.systemstate.SystemStateCache
 * @see com.dadcoach.systemstate.SystemStateCacheFilter
 * @see com.dadcoach.systemstate.AvailableSlot
 * @see <a href="Requirements 2">Read Before Write Principle</a>
 */
package com.dadcoach.systemstate;
