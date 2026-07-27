package com.dadcoach.domain.memory;

/**
 * Status lifecycle for Memory entities.
 *
 * <p>Memories start as ACTIVE and can be transitioned to:
 * <ul>
 *   <li>EXPIRED — low confidence and not accessed recently</li>
 *   <li>SUPERSEDED — replaced by corrected information</li>
 *   <li>ARCHIVED — removed due to capacity constraints or manual archival</li>
 * </ul>
 */
public enum MemoryStatus {
    ACTIVE,
    EXPIRED,
    SUPERSEDED,
    ARCHIVED
}
