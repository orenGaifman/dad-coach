/**
 * Implementation classes for the Mission service abstraction layer.
 * 
 * <p>This package contains concrete implementations of the {@link com.dadcoach.mission.MissionService}
 * interface for different mission types.</p>
 * 
 * <p><strong>MVP Implementation:</strong></p>
 * <ul>
 *   <li>{@link com.dadcoach.mission.impl.QualityTimeMissionService} - Handles Quality Time missions</li>
 *   <li>{@link com.dadcoach.mission.impl.QualityTimeMissionAdapter} - Adapts QualityTime entities to Mission interface</li>
 * </ul>
 * 
 * <p><strong>Architecture Note:</strong></p>
 * These implementations delegate to the underlying domain services (e.g., QualityTimeService)
 * while providing a unified Mission interface for the workflow engine.
 * 
 * @see com.dadcoach.mission.MissionService
 * @see com.dadcoach.mission.Mission
 */
package com.dadcoach.mission.impl;
