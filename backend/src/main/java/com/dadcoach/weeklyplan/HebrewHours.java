package com.dadcoach.weeklyplan;

/**
 * Time in the words a father reads (D-032): the week is told in HOURS - "שעה מתוך 3 שעות", never "60 מתוך 180
 * דקות". The coach copies these phrases from the weekly plan and the tool results (coverage.in_hours) instead of
 * converting minutes itself; Dad Coach's fixed messages use the same words.
 */
public final class HebrewHours {

    private HebrewHours() {
    }

    /** 60 → "שעה", 90 → "שעה וחצי", 120 → "שעתיים", 195 → "3 שעות ורבע", 30 → "חצי שעה", 0 → "0 שעות". */
    public static String of(int totalMinutes) {
        int minutes = Math.max(0, totalMinutes);
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) {
            return switch (rest) {
                case 0 -> "0 שעות";
                case 15 -> "רבע שעה";
                case 30 -> "חצי שעה";
                case 45 -> "שלושת רבעי שעה";
                default -> rest + " דקות";
            };
        }
        String h = switch (hours) {
            case 1 -> "שעה";
            case 2 -> "שעתיים";
            default -> hours + " שעות";
        };
        return switch (rest) {
            case 0 -> h;
            case 15 -> h + " ורבע";
            case 30 -> h + " וחצי";
            default -> h + " ו-" + rest + " דקות";
        };
    }
}
