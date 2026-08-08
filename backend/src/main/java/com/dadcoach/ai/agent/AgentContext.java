package com.dadcoach.ai.agent;

import com.dadcoach.systemstate.SystemState;
import com.dadcoach.workflow.WorkflowState;

import java.util.List;
import java.util.UUID;

/**
 * Context provided to the AI agent for decision making.
 * 
 * <p>This record contains all the information the AI needs to understand
 * the user's intent and choose the appropriate tool to invoke.</p>
 * 
 * @param fatherId the father's unique identifier
 * @param fatherName the father's display name
 * @param currentState the current workflow state
 * @param inboundMessage the message from the user
 * @param systemState the complete system state (can be null)
 * @param conversationHistory recent messages for context
 * @param availableTools list of tools the AI can use in this state
 */
public record AgentContext(
    UUID fatherId,
    String fatherName,
    WorkflowState currentState,
    String inboundMessage,
    SystemState systemState,
    List<ConversationTurn> conversationHistory,
    List<AgentTool> availableTools
) {
    
    /**
     * A single turn in the conversation.
     */
    public record ConversationTurn(
        String role, // "user" or "assistant"
        String content
    ) {}
    
    /**
     * Build a context summary for the AI prompt.
     */
    public String buildContextSummary() {
        StringBuilder sb = new StringBuilder();
        
        // Father info
        sb.append("שם האב: ").append(fatherName != null ? fatherName : "לא ידוע").append("\n");
        sb.append("מצב נוכחי: ").append(currentState).append("\n");
        
        if (systemState != null) {
            // Calendar connection status - important for scheduling!
            boolean calendarConnected = systemState.hasGoogleCalendarConnected();
            sb.append("יומן גוגל: ").append(calendarConnected ? "מחובר ✓" : "לא מחובר ❌").append("\n");
            
            // Weekly goal - IMPORTANT for guiding the conversation!
            if (systemState.weeklyGoalInfo() != null && systemState.weeklyGoalInfo().hasGoal()) {
                var goal = systemState.weeklyGoalInfo();
                sb.append("\n📎 יעד שבועי:\n");
                sb.append("  יעד: ").append(goal.targetQualityTimes()).append(" זמני איכות\n");
                sb.append("  הושלמו: ").append(goal.completedQualityTimes()).append("\n");
                sb.append("  מתוכננים: ").append(goal.scheduledQualityTimes()).append("\n");
                sb.append("  נשארו להשלמת היעד: ").append(goal.remainingToGoal()).append("\n");
            } else {
                sb.append("\n⚠️ אין יעד שבועי מוגדר - הצע לאב לקבוע יעד!\n");
            }
            
            // Children info
            if (systemState.fatherProfile() != null && systemState.fatherProfile().children() != null) {
                sb.append("\nילדים: ");
                var children = systemState.fatherProfile().children();
                for (int i = 0; i < children.size(); i++) {
                    var child = children.get(i);
                    sb.append(i + 1).append(". ").append(child.name())
                      .append(" (גיל ").append(child.age()).append(")");
                    if (i < children.size() - 1) sb.append(", ");
                }
                sb.append("\n");
            }
            
            // Next scheduled quality time
            var nextQT = systemState.getNextScheduledQualityTime();
            if (nextQT != null) {
                sb.append("זמן איכות קרוב: ").append(formatInstant(nextQT.scheduledStart()))
                  .append(" עם ").append(nextQT.childName()).append("\n");
            } else {
                sb.append("אין זמן איכות מתוכנן כרגע\n");
            }
            
            // Recent quality times history
            if (systemState.qualityTimeEvents() != null && !systemState.qualityTimeEvents().isEmpty()) {
                var recentCompleted = systemState.qualityTimeEvents().stream()
                    .filter(qt -> "COMPLETED".equals(qt.status()))
                    .sorted((a, b) -> b.completedAt() != null && a.completedAt() != null 
                        ? b.completedAt().compareTo(a.completedAt()) : 0)
                    .limit(3)
                    .toList();
                
                if (!recentCompleted.isEmpty()) {
                    sb.append("זמני איכות אחרונים שהושלמו:\n");
                    for (var qt : recentCompleted) {
                        sb.append("  - ").append(qt.childName());
                        if (qt.completedAt() != null) {
                            sb.append(" (").append(formatInstant(qt.completedAt())).append(")");
                        }
                        if (qt.completionNotes() != null && !qt.completionNotes().isEmpty()) {
                            sb.append(" - ").append(truncateNotes(qt.completionNotes(), 50));
                        }
                        sb.append("\n");
                    }
                }
            }
            
            // Dashboard metrics
            if (systemState.dashboardMetrics() != null) {
                var metrics = systemState.dashboardMetrics();
                sb.append("\nהתקדמות:\n");
                sb.append("  חגורה: ").append(metrics.currentBelt()).append("\n");
                sb.append("  רצף נוכחי: ").append(metrics.currentStreak()).append(" זמני איכות רצופים\n");
                sb.append("  סה\"כ זמני איכות: ").append(metrics.totalCompleted()).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Format an Instant to a readable Hebrew date/time string.
     */
    private String formatInstant(java.time.Instant instant) {
        if (instant == null) return "לא ידוע";
        var zoned = instant.atZone(java.time.ZoneId.of("Asia/Jerusalem"));
        var formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM בשעה HH:mm");
        return zoned.format(formatter);
    }
    
    /**
     * Truncate notes to a maximum length.
     */
    private String truncateNotes(String notes, int maxLength) {
        if (notes == null) return "";
        if (notes.length() <= maxLength) return notes;
        return notes.substring(0, maxLength) + "...";
    }
    
    /**
     * Get available time slots description for the AI.
     */
    public String getAvailableSlotsDescription() {
        StringBuilder sb = new StringBuilder("הימים הזמינים:\n");
        // This will be populated from the actual available slots
        sb.append("1. היום\n");
        sb.append("2. מחר\n");
        sb.append("3. מחרתיים\n");
        sb.append("4. עוד 3 ימים\n");
        sb.append("5. עוד 4 ימים\n");
        return sb.toString();
    }
    
    /**
     * Build the conversation history for the AI prompt.
     */
    public String buildConversationHistory() {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder("היסטוריית שיחה אחרונה:\n");
        for (ConversationTurn turn : conversationHistory) {
            String label = "user".equals(turn.role()) ? "אב" : "מערכת";
            sb.append(label).append(": ").append(turn.content()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * Create a builder for AgentContext.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UUID fatherId;
        private String fatherName;
        private WorkflowState currentState;
        private String inboundMessage;
        private SystemState systemState;
        private List<ConversationTurn> conversationHistory = List.of();
        private List<AgentTool> availableTools = List.of();
        
        public Builder fatherId(UUID fatherId) {
            this.fatherId = fatherId;
            return this;
        }
        
        public Builder fatherName(String fatherName) {
            this.fatherName = fatherName;
            return this;
        }
        
        public Builder currentState(WorkflowState currentState) {
            this.currentState = currentState;
            return this;
        }
        
        public Builder inboundMessage(String inboundMessage) {
            this.inboundMessage = inboundMessage;
            return this;
        }
        
        public Builder systemState(SystemState systemState) {
            this.systemState = systemState;
            return this;
        }
        
        public Builder conversationHistory(List<ConversationTurn> conversationHistory) {
            this.conversationHistory = conversationHistory;
            return this;
        }
        
        public Builder availableTools(List<AgentTool> availableTools) {
            this.availableTools = availableTools;
            return this;
        }
        
        public AgentContext build() {
            return new AgentContext(fatherId, fatherName, currentState, inboundMessage, 
                                    systemState, conversationHistory, availableTools);
        }
    }
}
