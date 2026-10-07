package com.dadcoach.web.father;

import com.dadcoach.auth.DashboardSession;
import com.dadcoach.auth.SessionService;
import com.dadcoach.calendar.GoogleCalendarService;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.integration.platform.lifecycle.PlatformPersonDeletions;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.web.common.WebException;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * הגדרות: name, timezone, Google Calendar (optional - sessions live in Dad Coach either way, D-007), and deleting his
 * data. Deletion is the existing self-service path (FatherApiServiceImpl / WhatsApp "delete my data"): DELETED at
 * once (nothing is sent to him any more), every dashboard session revoked, and the platform person-deletion outbox
 * (PlatformPersonDeletions, purgeLocal=true) deletes him on the platform and then purges his Dad Coach data.
 */
@Service
public class SettingsService {

    /** What the father types to confirm deleting his data. */
    public static final String DELETE_CONFIRMATION = "מחיקה";
    /** Where Google sends him back after connecting (a dashboard path; the signed OAuth state carries it). */
    static final String CALENDAR_RETURN_PATH = "/settings";

    public record SettingsView(String name, String timezone, boolean calendarConnected, boolean calendarAvailable,
                               String deleteConfirmation) {
    }

    private final FatherRepository fathers;
    private final GoogleCalendarService calendar;
    private final WeeklyGoalService weeklyGoals;
    private final PlatformPersonDeletions deletions;
    private final SessionService sessions;
    private final String googleClientId;

    public SettingsService(FatherRepository fathers, GoogleCalendarService calendar, WeeklyGoalService weeklyGoals,
                           PlatformPersonDeletions deletions, SessionService sessions,
                           @Value("${google.calendar.client-id:}") String googleClientId) {
        this.fathers = fathers;
        this.calendar = calendar;
        this.weeklyGoals = weeklyGoals;
        this.deletions = deletions;
        this.sessions = sessions;
        this.googleClientId = googleClientId == null ? "" : googleClientId;
    }

    public SettingsView view(Father father) {
        return new SettingsView(father.getDisplayName() == null ? "" : father.getDisplayName(),
                weeklyGoals.zoneFor(father).getId(), father.hasGoogleCalendarConfigured(), calendarAvailable(),
                DELETE_CONFIRMATION);
    }

    @Transactional
    public SettingsView update(Father father, String name, String timezone) {
        if (name != null) {
            String trimmed = name.strip();
            if (trimmed.isEmpty() || trimmed.length() > 120) {
                throw WebException.badRequest("INVALID_NAME", "a name of 1-120 characters");
            }
            father.setDisplayName(trimmed);
        }
        if (timezone != null) {
            try {
                father.setTimezone(ZoneId.of(timezone.strip()).getId());
            } catch (DateTimeException e) {
                throw WebException.badRequest("INVALID_TIMEZONE", "unknown timezone");
            }
        }
        return view(fathers.save(father));
    }

    /** A Google consent address with a signed state for this father (the same signer the AI's connect tool uses). */
    public String calendarConnectUrl(Father father) {
        if (!calendarAvailable()) {
            throw WebException.conflict("CALENDAR_NOT_CONFIGURED", "Google Calendar is not configured here");
        }
        return calendar.getAuthorizationUrl(father.getId(), CALENDAR_RETURN_PATH);
    }

    @Transactional
    public SettingsView disconnectCalendar(Father father) {
        if (father.hasGoogleCalendarConfigured()) {
            calendar.disconnectCalendar(father);
        }
        return view(fathers.findById(father.getId()).orElse(father));
    }

    @Transactional
    public void deleteMyData(Father father, String confirmation) {
        if (confirmation == null || !DELETE_CONFIRMATION.equals(confirmation.strip())) {
            throw WebException.badRequest("CONFIRMATION_MISMATCH", "type the confirmation word");
        }
        // A father still onboarding may delete too (the WhatsApp phrase hands that case to an operator; here he is
        // signed in and typed the confirmation).
        if (father.getStatus().canTransitionTo(FatherStatus.DELETED)) {
            father.transitionTo(FatherStatus.DELETED);
        } else {
            father.setStatus(FatherStatus.DELETED);
        }
        fathers.save(father);
        sessions.revokeAllForFather(father.getId(), DashboardSession.REASON_DELETED);
        deletions.request(father.getId(), father.getPhone(), true);
    }

    private boolean calendarAvailable() {
        return !googleClientId.isBlank();
    }
}
