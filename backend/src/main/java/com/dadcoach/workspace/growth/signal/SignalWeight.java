package com.dadcoach.workspace.growth.signal;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration class mapping each {@link GrowthSignalType} to its point value (Signal_Weight).
 *
 * <p>Point values are defined per Requirement 11.2 and represent the contribution
 * of each signal type to the father's overall Growth_Score. This class is a final
 * utility class — instantiation is not allowed.</p>
 *
 * <p>The mapping is stored in an unmodifiable {@link EnumMap} for O(1) lookup performance
 * and type safety.</p>
 *
 * @see GrowthSignalType
 */
public final class SignalWeight {

    private static final Map<GrowthSignalType, Integer> WEIGHTS;

    static {
        EnumMap<GrowthSignalType, Integer> map = new EnumMap<>(GrowthSignalType.class);
        map.put(GrowthSignalType.MISSION_COMPLETED, 10);
        map.put(GrowthSignalType.MISSION_REFLECTED, 5);
        map.put(GrowthSignalType.GOAL_PROGRESS, 15);
        map.put(GrowthSignalType.GOAL_COMPLETED, 50);
        map.put(GrowthSignalType.MEANINGFUL_CONVERSATION, 8);
        map.put(GrowthSignalType.DAILY_ENGAGEMENT, 3);
        map.put(GrowthSignalType.STREAK_BONUS_7, 20);
        map.put(GrowthSignalType.STREAK_BONUS_14, 30);
        map.put(GrowthSignalType.STREAK_BONUS_21, 40);
        map.put(GrowthSignalType.STREAK_BONUS_30, 50);
        map.put(GrowthSignalType.STREAK_BONUS_60, 75);
        map.put(GrowthSignalType.STREAK_BONUS_90, 100);
        map.put(GrowthSignalType.STREAK_BONUS_180, 150);
        map.put(GrowthSignalType.STREAK_BONUS_365, 300);
        map.put(GrowthSignalType.QUALITY_TIME_REPORTED, 12);
        map.put(GrowthSignalType.POSITIVE_ACTIVITY, 5);
        WEIGHTS = Collections.unmodifiableMap(map);
    }

    private SignalWeight() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /**
     * Returns the point value for the given signal type.
     *
     * @param type the growth signal type
     * @return the number of points awarded for this signal type
     * @throws IllegalArgumentException if type is null or has no configured weight
     */
    public static int getPoints(GrowthSignalType type) {
        if (type == null) {
            throw new IllegalArgumentException("Signal type must not be null");
        }
        Integer points = WEIGHTS.get(type);
        if (points == null) {
            throw new IllegalArgumentException("No weight configured for signal type: " + type);
        }
        return points;
    }

    /**
     * Returns an unmodifiable view of all signal type to point value mappings.
     *
     * @return unmodifiable map of signal types to their point values
     */
    public static Map<GrowthSignalType, Integer> getAllWeights() {
        return WEIGHTS;
    }
}
