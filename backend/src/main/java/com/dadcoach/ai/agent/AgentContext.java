package com.dadcoach.ai.agent;

import com.dadcoach.common.AppConstants;
import com.dadcoach.systemstate.AvailableSlot;
import com.dadcoach.systemstate.SystemState;
import com.dadcoach.workflow.WelcomeStep;
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
 * @param welcomeStep the current step within the WELCOME state (null if not in welcome)
 * @param inboundMessage the message from the user
 * @param systemState the complete system state (can be null)
 * @param conversationHistory recent messages for context
 * @param availableTools list of tools the AI can use in this state
 * @param availableSlots list of free time slots from Google Calendar (can be null/empty)
 */
public record AgentContext(
    UUID fatherId,
    String fatherName,
    WorkflowState currentState,
    WelcomeStep welcomeStep,
    String inboundMessage,
    SystemState systemState,
    List<ConversationTurn> conversationHistory,
    List<AgentTool> availableTools,
    List<AvailableSlot> availableSlots
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
        
        // Get timezone from system state if available
        String timezone = AppConstants.DEFAULT_TIMEZONE;
        if (systemState != null && systemState.fatherProfile() != null && 
            systemState.fatherProfile().timezone() != null) {
            timezone = systemState.fatherProfile().timezone();
        }
        java.time.ZoneId zoneId = java.time.ZoneId.of(timezone);
        
        // Current date, time and day of week - CRITICAL for time calculations
        var nowDateTime = java.time.ZonedDateTime.now(zoneId);
        var now = nowDateTime.toLocalDate();
        var hebrewDayName = getHebrewDayName(now.getDayOfWeek());
        var currentTime = nowDateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        sb.append("📅 היום: ").append(hebrewDayName).append(" (").append(now.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))).append(")\n");
        sb.append("🕐 השעה הנוכחית: ").append(currentTime).append("\n");
        
        // Father info
        sb.append("שם האב: ").append(fatherName != null ? fatherName : "לא ידוע").append("\n");
        sb.append("מצב נוכחי: ").append(currentState).append("\n");
        
        // Welcome step - CRITICAL for step-by-step onboarding!
        if (currentState == WorkflowState.WELCOME && welcomeStep != null) {
            sb.append("\n🎯 **שלב בתהליך ההצטרפות:** ").append(welcomeStep.name());
            sb.append(" (שלב ").append(welcomeStep.getStepNumber()).append(" מתוך ").append(WelcomeStep.getTotalSteps()).append(")\n");
            sb.append("⚠️ **חשוב:** עקוב בדיוק אחרי השלב הנוכחי! לא לדלג קדימה!\n\n");
        }
        
        if (systemState != null) {
            // NOTE: Calendar connection status removed from context.
            // Calendar connection is handled during web onboarding, not WhatsApp.
            // Showing the status here was causing the AI to ask users to connect calendar.
            
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
        var zoned = instant.atZone(AppConstants.DEFAULT_ZONE_ID);
        var formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM בשעה HH:mm");
        return zoned.format(formatter);
    }
    
    /**
     * Get Hebrew name for day of week.
     */
    private String getHebrewDayName(java.time.DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case SUNDAY -> "יום ראשון";
            case MONDAY -> "יום שני";
            case TUESDAY -> "יום שלישי";
            case WEDNESDAY -> "יום רביעי";
            case THURSDAY -> "יום חמישי";
            case FRIDAY -> "יום שישי";
            case SATURDAY -> "יום שבת";
        };
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
     * Uses real calendar data when available, otherwise returns generic options.
     */
    public String getAvailableSlotsDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Check if we have real calendar slots
        if (availableSlots != null && !availableSlots.isEmpty()) {
            sb.append("📅 **זמנים פנויים ביומן שלך:**\n\n");
            
            java.time.ZoneId israelZone = AppConstants.DEFAULT_ZONE_ID;
            java.time.LocalDate lastDate = null;
            int slotNum = 0;
            
            for (AvailableSlot slot : availableSlots) {
                java.time.ZonedDateTime start = slot.startTime().atZone(israelZone);
                java.time.ZonedDateTime end = slot.endTime().atZone(israelZone);
                java.time.LocalDate slotDate = start.toLocalDate();
                
                // Add day header if new day
                if (!slotDate.equals(lastDate)) {
                    slotNum++;
                    lastDate = slotDate;
                    String hebrewDay = getHebrewDayName(slotDate.getDayOfWeek());
                    String dateStr = slotDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
                    sb.append(String.format("\n**%d. %s (%s)**\n", slotNum, hebrewDay, dateStr));
                }
                
                // Add time slot
                String startTime = start.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                String endTime = end.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                sb.append(String.format("   ⏰ %s - %s (פנוי)\n", startTime, endTime));
            }
            
            sb.append("\n**טיפ:** כשהאב אומר יום, השתמש במספר היום (day_selection) מהרשימה למעלה.");
            
        } else {
            // Fallback - no calendar data
            sb.append("הימים הזמינים:\n");
            sb.append("1. היום\n");
            sb.append("2. מחר\n");
            sb.append("3. מחרתיים\n");
            sb.append("4. עוד 3 ימים\n");
            sb.append("5. עוד 4 ימים\n");
            sb.append("\n⚠️ לא נטענו זמנים מהיומן - ודא שיומן גוגל מחובר");
        }
        
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
        private WelcomeStep welcomeStep;
        private String inboundMessage;
        private SystemState systemState;
        private List<ConversationTurn> conversationHistory = List.of();
        private List<AgentTool> availableTools = List.of();
        private List<AvailableSlot> availableSlots = List.of();
        
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
        
        public Builder welcomeStep(WelcomeStep welcomeStep) {
            this.welcomeStep = welcomeStep;
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
        
        public Builder availableSlots(List<AvailableSlot> availableSlots) {
            this.availableSlots = availableSlots;
            return this;
        }
        
        public AgentContext build() {
            return new AgentContext(fatherId, fatherName, currentState, welcomeStep, inboundMessage, 
                                    systemState, conversationHistory, availableTools, availableSlots);
        }
    }
}
