package com.dadcoach.workspace.growth.celebration;

/**
 * Types of celebration events in the growth system.
 *
 * <p>Each type corresponds to a distinct achievement trigger that
 * generates a celebratory notification for the father.</p>
 *
 * @see CelebrationEvent
 */
public enum CelebrationEventType {

    /** Father advanced to a higher belt level. */
    BELT_LEVEL_UP,

    /** Father earned a new achievement. */
    ACHIEVEMENT_EARNED,

    /** Father reached a milestone. */
    MILESTONE_REACHED,

    /** Father hit a streak milestone (7, 14, 21, 30, 60, 90, 180, 365 days). */
    STREAK_MILESTONE
}
