/**
 * Quality Time Commitment system.
 * 
 * <p>This package handles father commitments to spend quality time with their children.
 * The goal is to quickly get fathers to commit to a specific time, then remind them
 * 30 minutes before the scheduled time.
 * 
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link com.dadcoach.workspace.commitment.QualityTimeCommitment} - Entity representing a commitment</li>
 *   <li>{@link com.dadcoach.workspace.commitment.CommitmentService} - Business logic for managing commitments</li>
 *   <li>{@link com.dadcoach.workspace.commitment.CommitmentReminderScheduler} - Sends 30-minute reminders</li>
 *   <li>{@link com.dadcoach.workspace.commitment.CommitmentExtractor} - Extracts commitments from conversation</li>
 *   <li>{@link com.dadcoach.workspace.commitment.CommitmentController} - REST API for dashboard</li>
 * </ul>
 * 
 * <h2>Flow</h2>
 * <ol>
 *   <li>Father mentions a time in WhatsApp conversation ("יום ראשון ב-17:00")</li>
 *   <li>AI recognizes commitment and calls CommitmentService to create it</li>
 *   <li>Dashboard shows upcoming commitments</li>
 *   <li>30 minutes before: CommitmentReminderScheduler sends WhatsApp reminder</li>
 *   <li>After time: Father reports completion or commitment is marked as missed</li>
 * </ol>
 */
package com.dadcoach.workspace.commitment;
