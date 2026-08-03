/**
 * Logging utilities for the workflow engine.
 * 
 * <p>This package provides structured logging support for the deterministic workflow engine,
 * including MDC (Mapped Diagnostic Context) management for father_id tracking.</p>
 * 
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link com.dadcoach.workflow.logging.WorkflowLoggingContext} - MDC management for workflow operations</li>
 * </ul>
 * 
 * <p>Implements Requirement 16 (Operational Observability) from the deterministic-workflow-engine spec:</p>
 * <ul>
 *   <li>16.4: Log fallback usage as warnings for AI reliability monitoring</li>
 *   <li>16.6: Include father_id in all logs for filtering by user journey</li>
 * </ul>
 * 
 * @see com.dadcoach.workflow.logging.WorkflowLoggingContext
 */
package com.dadcoach.workflow.logging;
