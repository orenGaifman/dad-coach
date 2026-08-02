package com.dadcoach.workspace.magiclink;

import org.springframework.stereotype.Component;

/**
 * Appends dashboard links to WhatsApp coaching messages.
 * 
 * This service is called after the AI generates a coaching response,
 * when specific events occur that warrant sending a dashboard link
 * (e.g., quality time logged, achievement earned, belt level up).
 * 
 * Usage:
 * <pre>
 * String linkMessage = dashboardLinkAppender.generateLinkMessage(
 *     fatherId, 
 *     DashboardLinkContext.QUALITY_TIME_LOGGED
 * );
 * String finalMessage = coachingResponse + "\n\n" + linkMessage;
 * </pre>
 */
@Component
public class DashboardLinkAppender {

    private final MagicLinkService magicLinkService;

    public DashboardLinkAppender(MagicLinkService magicLinkService) {
        this.magicLinkService = magicLinkService;
    }

    /**
     * Generates a WhatsApp-formatted message with a dashboard link.
     * 
     * @param fatherId The father to generate the link for
     * @param context The context/reason for the link
     * @return A formatted message string with emoji and link
     */
    public String generateLinkMessage(Long fatherId, DashboardLinkContext context) {
        String redirectPath = context.getRedirectPath();
        String magicLink = magicLinkService.generateMagicLink(
                fatherId, 
                redirectPath, 
                context.name().toLowerCase()
        );

        return context.formatMessage(magicLink);
    }

    /**
     * Checks if a dashboard link should be included based on the conversation context.
     * 
     * @param hasQualityTimeLogged Whether quality time was logged in this conversation
     * @param hasAchievementEarned Whether an achievement was earned
     * @param hasBeltLevelUp Whether the father leveled up their belt
     * @return The appropriate context to use, or null if no link should be added
     */
    public DashboardLinkContext determineContext(
            boolean hasQualityTimeLogged,
            boolean hasAchievementEarned, 
            boolean hasBeltLevelUp) {
        
        // Priority: belt level up > achievement > quality time
        if (hasBeltLevelUp) {
            return DashboardLinkContext.BELT_LEVEL_UP;
        }
        if (hasAchievementEarned) {
            return DashboardLinkContext.ACHIEVEMENT_EARNED;
        }
        if (hasQualityTimeLogged) {
            return DashboardLinkContext.QUALITY_TIME_LOGGED;
        }
        return null;
    }

    /**
     * Context types for dashboard links sent via WhatsApp.
     * Each context determines:
     * - The redirect path in the dashboard
     * - The message template shown to the father
     */
    public enum DashboardLinkContext {
        /**
         * After logging quality time with a child.
         */
        QUALITY_TIME_LOGGED("/growth/achievements", """
            📊 *Tu progreso está actualizado*
            Mira tus logros en el dashboard: %s
            """),

        /**
         * After earning an achievement.
         */
        ACHIEVEMENT_EARNED("/growth/achievements", """
            🏆 *¡Logro desbloqueado!*
            Mira tu nuevo logro: %s
            """),

        /**
         * After leveling up belt.
         */
        BELT_LEVEL_UP("/growth", """
            🥋 *¡Subiste de nivel!*
            Mira tu nuevo cinturón: %s
            """),

        /**
         * Streak milestone reached.
         */
        STREAK_MILESTONE("/growth/streak", """
            🔥 *¡Racha increíble!*
            Mira tu racha de días: %s
            """),

        /**
         * Weekly check-in reminder.
         */
        WEEKLY_CHECKIN("/dashboard", """
            📅 *Tu resumen semanal*
            Mira cómo te fue esta semana: %s
            """),

        /**
         * Prompt to log activity.
         */
        LOG_ACTIVITY_PROMPT("/coaching/log", """
            ✍️ *Registra tu actividad*
            Accede aquí para registrar: %s
            """);

        private final String redirectPath;
        private final String messageTemplate;

        DashboardLinkContext(String redirectPath, String messageTemplate) {
            this.redirectPath = redirectPath;
            this.messageTemplate = messageTemplate;
        }

        public String getRedirectPath() {
            return redirectPath;
        }

        /**
         * Formats the message with the provided link.
         */
        public String formatMessage(String link) {
            return String.format(messageTemplate.trim(), link);
        }
    }
}
