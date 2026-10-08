package com.dadcoach.replies;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Days and times in the words the father reads, worked out in code so the coach never names a weekday itself
 * (production 2026-10-07: "מחר (חמישי)" built from English day names; qa-lab: Thursday 8.10 told as "מחר, יום שישי").
 */
public final class HebrewWhen {

    private static final String[] DAYS = {"שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת", "ראשון"};
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private HebrewWhen() {
    }

    /** "היום, יום חמישי 8.10 ב-17:00" / "מחר, יום שישי 9.10 ב-09:00" / "יום ראשון 11.10 ב-18:30". */
    public static String label(ZonedDateTime localStart, LocalDate today) {
        LocalDate day = localStart.toLocalDate();
        String prefix = day.equals(today) ? "היום, " : day.equals(today.plusDays(1)) ? "מחר, " : "";
        return prefix + date(day) + " ב-" + time(localStart.toLocalTime());
    }

    /** "יום חמישי 8.10". */
    public static String date(LocalDate day) {
        return "יום " + DAYS[day.getDayOfWeek().getValue() - 1] + " " + day.getDayOfMonth() + "." + day.getMonthValue();
    }

    /** When a moment falls, short: "היום ב-16:00", "מחר ב-08:00", "ביום שישי 9.10 ב-08:00". */
    public static String at(ZonedDateTime local, LocalDate today) {
        LocalDate day = local.toLocalDate();
        String prefix = day.equals(today) ? "היום" : day.equals(today.plusDays(1)) ? "מחר" : "ב" + date(day);
        return prefix + " ב-" + time(local.toLocalTime());
    }

    public static String time(LocalTime time) {
        return time.format(HH_MM);
    }
}
