package com.dadcoach.memory.dto;

import java.util.UUID;

/**
 * Data Transfer Object representing memory capacity information for a father.
 *
 * <p>From SPEC-004 Requirement 15 (Memory Capacity):
 * Maximum 500 active memories per father. This DTO provides capacity metrics
 * to help manage memory usage.
 *
 * <h3>Capacity Enforcement</h3>
 * <p>When a father reaches the 500-memory limit:
 * <ul>
 *   <li>New memory creation triggers archival of lowest-scoring memory</li>
 *   <li>Score = importance × confidence</li>
 *   <li>Only ACTIVE and CONFIRMED memories count toward the limit</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * MemoryCapacityDto capacity = memoryService.getCapacity(fatherId);
 * if (capacity.isNearCapacity()) {
 *     // Alert user or trigger cleanup
 * }
 * }</pre>
 *
 * <p><b>Validates: SPEC-004 Requirement 15 - Capacity Limits</b>
 *
 * @see com.dadcoach.memory.MemoryFacadeService#getCapacity(UUID)
 */
public class MemoryCapacityDto {

    /**
     * Maximum active memories allowed per father (SPEC-004 Req 15).
     */
    public static final long MAX_MEMORIES_PER_FATHER = 500;

    /**
     * Threshold percentage for "near capacity" warning (90%).
     */
    public static final double NEAR_CAPACITY_THRESHOLD = 0.90;

    /**
     * The father's ID.
     */
    private final UUID fatherId;

    /**
     * Current count of active memories (ACTIVE + CONFIRMED states).
     */
    private final long currentCount;

    /**
     * Maximum allowed memories per father.
     */
    private final long maxAllowed;

    /**
     * Number of memories that can still be created.
     */
    private final long availableCapacity;

    /**
     * Usage percentage (0.0 to 1.0).
     */
    private final double usagePercentage;

    /**
     * Creates a new MemoryCapacityDto.
     *
     * @param fatherId     the father's ID
     * @param currentCount current number of active memories
     */
    public MemoryCapacityDto(UUID fatherId, long currentCount) {
        this.fatherId = fatherId;
        this.currentCount = currentCount;
        this.maxAllowed = MAX_MEMORIES_PER_FATHER;
        this.availableCapacity = Math.max(0, maxAllowed - currentCount);
        this.usagePercentage = (double) currentCount / maxAllowed;
    }

    /**
     * Creates a new MemoryCapacityDto with custom max allowed (for testing).
     *
     * @param fatherId     the father's ID
     * @param currentCount current number of active memories
     * @param maxAllowed   maximum allowed memories
     */
    public MemoryCapacityDto(UUID fatherId, long currentCount, long maxAllowed) {
        this.fatherId = fatherId;
        this.currentCount = currentCount;
        this.maxAllowed = maxAllowed;
        this.availableCapacity = Math.max(0, maxAllowed - currentCount);
        this.usagePercentage = maxAllowed > 0 ? (double) currentCount / maxAllowed : 1.0;
    }

    /**
     * Returns the father's ID.
     *
     * @return the father's ID
     */
    public UUID getFatherId() {
        return fatherId;
    }

    /**
     * Returns the current count of active memories.
     *
     * @return current memory count (ACTIVE + CONFIRMED)
     */
    public long getCurrentCount() {
        return currentCount;
    }

    /**
     * Returns the maximum allowed memories.
     *
     * @return max allowed (typically 500)
     */
    public long getMaxAllowed() {
        return maxAllowed;
    }

    /**
     * Returns the available capacity (how many more can be created).
     *
     * @return available capacity (maxAllowed - currentCount, minimum 0)
     */
    public long getAvailableCapacity() {
        return availableCapacity;
    }

    /**
     * Returns the usage percentage as a decimal (0.0 to 1.0).
     *
     * @return usage percentage
     */
    public double getUsagePercentage() {
        return usagePercentage;
    }

    /**
     * Returns the usage percentage as a formatted string (e.g., "75%").
     *
     * @return formatted usage percentage
     */
    public String getUsagePercentageFormatted() {
        return String.format("%.0f%%", usagePercentage * 100);
    }

    /**
     * Checks if the father is at full capacity.
     *
     * @return true if currentCount >= maxAllowed
     */
    public boolean isAtCapacity() {
        return currentCount >= maxAllowed;
    }

    /**
     * Checks if the father is near capacity (>= 90% used).
     *
     * @return true if usage percentage >= 90%
     */
    public boolean isNearCapacity() {
        return usagePercentage >= NEAR_CAPACITY_THRESHOLD;
    }

    /**
     * Checks if there is available capacity for new memories.
     *
     * @return true if availableCapacity > 0
     */
    public boolean hasAvailableCapacity() {
        return availableCapacity > 0;
    }

    @Override
    public String toString() {
        return "MemoryCapacityDto{" +
                "fatherId=" + fatherId +
                ", currentCount=" + currentCount +
                ", maxAllowed=" + maxAllowed +
                ", availableCapacity=" + availableCapacity +
                ", usagePercentage=" + getUsagePercentageFormatted() +
                '}';
    }
}
