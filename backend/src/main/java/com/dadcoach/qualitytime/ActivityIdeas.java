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

    public record ActivityIdea(String title, String description, int durationMinutes, boolean indoor) {
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
                            "בנו יחד מגדל מקוביות או לגו. תן לילד להוביל את הבנייה ולבחור את הצבעים.",
                            20, true));
                    allIdeas.add(new ActivityIdea("סיפור עם קולות",
                            "קראו יחד ספר אהוב ועשו קולות שונים לכל דמות. תן לילד לבחור את הקולות.",
                            15, true));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("ציד אוצרות בחצר",
                            "צאו לחצר או לפארק הקרוב וחפשו יחד אוצרות טבע: עלים, אבנים מיוחדות, או פרחים.",
                            30, false));
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
                            "הכינו יחד מתכון פשוט כמו פנקייקים או עוגיות. תן לילד למדוד חומרים ולערבב.",
                            30, true));
                    allIdeas.add(new ActivityIdea("משחק לוח",
                            "שחקו יחד במשחק לוח מתאים לגיל. זה מפתח חשיבה אסטרטגית.",
                            30, true));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("טיול אופניים",
                            "צאו לרכיבת אופניים יחד בפארק או בשכונה. זמן איכות נהדר לשיחה.",
                            45, false));
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
                            "בנו יחד משהו - בית ציפורים, מדף, או כל פרויקט יצירתי שהילד בוחר.",
                            45, true));
                    allIdeas.add(new ActivityIdea("לימוד מיומנות חדשה",
                            "למדו יחד משהו חדש - נגינה, שפה, או תכנות בסיסי.",
                            30, true));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("משחק כדורסל",
                            "צאו לשחק כדורסל או כדורגל יחד. זמן פעילות גופנית משותפת מחזק את הקשר.",
                            40, false));
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
