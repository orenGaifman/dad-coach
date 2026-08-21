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
     * @return A formatted message string with emoji and link (Hebrew default)
     */
    public String generateLinkMessage(Long fatherId, DashboardLinkContext context) {
        return generateLinkMessage(fatherId, context, "he");
    }

    /**
     * Generates a WhatsApp-formatted message with a dashboard link in the specified locale.
     * 
     * @param fatherId The father to generate the link for
     * @param context The context/reason for the link
     * @param locale The father's locale ("en" or "he")
     * @return A formatted message string with emoji and link
     */
    public String generateLinkMessage(Long fatherId, DashboardLinkContext context, String locale) {
        String redirectPath = context.getRedirectPath();
        String magicLink = magicLinkService.generateMagicLink(
                fatherId, 
                redirectPath, 
                context.name().toLowerCase()
        );

        return context.formatMessage(magicLink, locale);
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
         * Welcome message for new fathers after onboarding.
         * Links to the dashboard home to explore progress tracking.
         */
        WELCOME("/dashboard", """
            📱 *לוח הבקרה שלך*
            צפה בהתקדמות שלך כאן:
            %s
            """, """
            📱 *Your Dashboard*
            Track your progress here:
            %s
            """),

        /**
         * After logging quality time with a child.
         */
        QUALITY_TIME_LOGGED("/growth/achievements", """
            📊 *ההתקדמות שלך עודכנה*
            צפה בהישגים שלך בדשבורד:
            %s
            """, null),

        /**
         * After earning an achievement.
         */
        ACHIEVEMENT_EARNED("/growth/achievements", """
            🏆 *הישג חדש!*
            צפה בהישג החדש שלך:
            %s
            """, null),

        /**
         * After leveling up belt.
         */
        BELT_LEVEL_UP("/growth", """
            🥋 *עלית רמה!*
            צפה בחגורה החדשה שלך:
            %s
            """, null),

        /**
         * Streak milestone reached.
         */
        STREAK_MILESTONE("/growth/streak", """
            🔥 *רצף מדהים!*
            צפה ברצף הימים שלך:
            %s
            """, null),

        /**
         * Weekly check-in reminder.
         */
        WEEKLY_CHECKIN("/dashboard", """
            📅 *הסיכום השבועי שלך*
            ראה איך עבר לך השבוע:
            %s
            """, null),

        /**
         * Prompt to log activity.
         */
        LOG_ACTIVITY_PROMPT("/coaching/log", """
            ✍️ *דווח על פעילות*
            לחץ כאן לדיווח:
            %s
            """, null);

        private final String redirectPath;
        private final String hebrewTemplate;
        private final String englishTemplate;

        DashboardLinkContext(String redirectPath, String hebrewTemplate, String englishTemplate) {
            this.redirectPath = redirectPath;
            this.hebrewTemplate = hebrewTemplate;
            this.englishTemplate = englishTemplate;
        }

        public String getRedirectPath() {
            return redirectPath;
        }

        /**
         * Formats the message with the provided link (Hebrew default).
         */
        public String formatMessage(String link) {
            return formatMessage(link, "he");
        }

        /**
         * Formats the message with the provided link and locale.
         * 
         * @param link the dashboard magic link
         * @param locale the father's locale ("en" or "he")
         * @return formatted message in the appropriate language
         */
        public String formatMessage(String link, String locale) {
            String template = "en".equals(locale) && englishTemplate != null 
                    ? englishTemplate 
                    : hebrewTemplate;
            // Strip leading whitespace but keep structure - templates have proper line breaks
            return String.format(template.stripLeading(), link);
        }
    }
}
