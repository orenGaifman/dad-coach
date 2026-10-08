package com.dadcoach.qualitytime;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity ideas for a session with a child: a fixed, age-banded catalog (up to 5, 6-10, 11+), Hebrew or English,
 * indoor/outdoor. Deterministic and offline - the coach (the platform's AI) personalizes the wording; Dad Coach
 * itself calls no model (D-003).
 */
public final class ActivityIdeas {

    private ActivityIdeas() {
    }

    /**
     * @param line the idea as one short line for the father's WhatsApp (Hebrew only, null in English): "{child}" is
     *             the session's children, "{boy|girl|several}" the verb in their form - see {@link #line}
     */
    public record ActivityIdea(String title, String description, int durationMinutes, boolean indoor, String line) {
        ActivityIdea(String title, String description, int durationMinutes, boolean indoor) {
            this(title, description, durationMinutes, indoor, null);
        }
    }

    /** Who the idea is for, so its verb agrees: one boy, one girl, several children, or one child of unknown gender. */
    public enum Form { BOY, GIRL, SEVERAL, UNKNOWN }

    private static final java.util.regex.Pattern VERB = java.util.regex.Pattern.compile("\\{([^{}|]+)\\|([^{}|]+)\\|([^{}|]+)}");

    /**
     * The idea's short line with the children's names (D-032): "מגדל קוביות ענק, בצבעים לבחירת מאיה". The lines are
     * worded without a verb that needs the child's gender (it is often not known); a "{boy|girl|several}" verb is still
     * resolved by {@link Form} if a line uses one. No names: "הילדים".
     */
    public static String line(ActivityIdea idea, String names, Form form) {
        String text = idea.line() != null ? idea.line() : idea.title();
        boolean plural = form == Form.SEVERAL || form == Form.UNKNOWN || names == null;
        String who = names == null ? "הילדים" : names;
        java.util.regex.Matcher m = VERB.matcher(text.replace("{child}", who));
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(
                    plural ? m.group(3) : form == Form.GIRL ? m.group(2) : m.group(1)));
        }
        return m.appendTail(out).toString();
    }

    public static List<ActivityIdea> forChild(int childAge, String locale, String activityType) {
        List<ActivityIdea> allIdeas = new ArrayList<>();
        boolean hebrew = "he".equals(locale);
        boolean indoorOnly = "indoor".equalsIgnoreCase(activityType);
        boolean outdoorOnly = "outdoor".equalsIgnoreCase(activityType);

        if (childAge <= 5) {
            if (hebrew) {
                if (!outdoorOnly) {
                    allIdeas.add(new ActivityIdea("בניית מגדל קוביות",
                            "מגדל ענק מקוביות או לגו, והילד בוחר את הצבעים.", 20, true,
                            "מגדל קוביות ענק, בצבעים לבחירת {child}"));
                    allIdeas.add(new ActivityIdea("סיפור עם קולות",
                            "ספר אהוב, ואתה עושה קול אחר לכל דמות.", 15, true,
                            "ספר אהוב עם קול אחר לכל דמות"));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("ציד אוצרות בחצר",
                            "ציד אוצרות בחצר או בפארק: עלים, אבנים ופרחים.", 30, false,
                            "ציד אוצרות בחצר: עלים, אבנים ופרחים"));
                }
            } else {
                if (!outdoorOnly) {
                    allIdeas.add(new ActivityIdea("Building Block Tower",
                            "Build a tower together using blocks or Lego. Let your child lead the construction.",
                            20, true));
                    allIdeas.add(new ActivityIdea("Story Time with Voices",
                            "Read a favorite book together and use different voices for each character.",
                            15, true));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("Backyard Treasure Hunt",
                            "Go to the backyard or nearby park and search for nature treasures together.",
                            30, false));
                }
            }
        } else if (childAge <= 10) {
            if (hebrew) {
                if (!outdoorOnly) {
                    allIdeas.add(new ActivityIdea("בישול יחד",
                            "פנקייקים או עוגיות, והילד מודד ומערבב.", 30, true,
                            "פנקייקים יחד, עם {child} במדידות ובערבוב"));
                    allIdeas.add(new ActivityIdea("משחק לוח",
                            "משחק לוח שמתאים לגיל, שהילד בוחר.", 30, true,
                            "משחק לוח לבחירת {child}"));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("טיול אופניים",
                            "סיבוב אופניים בפארק או בשכונה, עם זמן לדבר.", 45, false,
                            "סיבוב אופניים בפארק או בשכונה"));
                }
            } else {
                if (!outdoorOnly) {
                    allIdeas.add(new ActivityIdea("Cooking Together",
                            "Make a simple recipe together like pancakes or cookies. Let your child help measure.",
                            30, true));
                    allIdeas.add(new ActivityIdea("Board Game",
                            "Play an age-appropriate board game together. Great for strategic thinking.",
                            30, true));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("Bike Ride",
                            "Go for a bike ride together in the park or neighborhood.",
                            45, false));
                }
            }
        } else {
            if (hebrew) {
                if (!outdoorOnly) {
                    allIdeas.add(new ActivityIdea("פרויקט בנייה",
                            "פרויקט בנייה שהילד בוחר, כמו בית ציפורים או מדף.", 45, true,
                            "פרויקט בנייה לבחירת {child}, כמו בית ציפורים או מדף"));
                    allIdeas.add(new ActivityIdea("לימוד מיומנות חדשה",
                            "ללמוד יחד משהו חדש: נגינה, שפה או קצת תכנות.", 30, true,
                            "ללמוד יחד משהו חדש: נגינה, שפה או קצת תכנות"));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("משחק כדורסל",
                            "כדורסל או כדורגל בחוץ, יחד.", 40, false,
                            "כדורסל או כדורגל בחוץ"));
                }
            } else {
                if (!outdoorOnly) {
                    allIdeas.add(new ActivityIdea("DIY Project",
                            "Build something together - a birdhouse, shelf, or any creative project.",
                            45, true));
                    allIdeas.add(new ActivityIdea("Learn a New Skill",
                            "Learn something new together - music, a language, or basic coding.",
                            30, true));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("Basketball Game",
                            "Go play basketball or soccer together. Physical activity strengthens your bond.",
                            40, false));
                }
            }
        }

        // Return first 3 ideas
        return allIdeas.subList(0, Math.min(3, allIdeas.size()));
    }
}
