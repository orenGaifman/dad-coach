package com.dadcoach.workspace.growth.score;

/**
 * Constants class defining scoring policy versions for the Growth System.
 *
 * <p>Each {@link com.dadcoach.workspace.growth.signal.GrowthSignal} record includes
 * a {@code scoring_policy_version} field indicating which scoring rules were in effect
 * when the signal was recorded (Design Decision AD-7: Forward-Only Scoring Policy Versioning).</p>
 *
 * <p>Key principles:</p>
 * <ul>
 *   <li>Existing awarded points are <strong>immutable</strong> — they are never recalculated
 *       when the scoring policy changes.</li>
 *   <li>New scoring rules apply only to <strong>new signals</strong> recorded after the
 *       policy version is incremented.</li>
 *   <li>The version number is monotonically increasing (forward-only).</li>
 *   <li>Old versions remain valid for historical signals and audit purposes.</li>
 * </ul>
 *
 * <p>When the scoring model evolves (e.g., adjusting signal weights), increment
 * {@link #CURRENT} and update {@link com.dadcoach.workspace.growth.signal.SignalWeight}
 * accordingly. Historical signals retain their original points and version tag.</p>
 *
 * @see com.dadcoach.workspace.growth.signal.GrowthSignal
 * @see com.dadcoach.workspace.growth.signal.SignalWeight
 */
public final class ScoringPolicyVersion {

    /**
     * The current scoring policy version.
     * All newly recorded growth signals use this version.
     */
    public static final int CURRENT = 1;

    private ScoringPolicyVersion() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }
}
