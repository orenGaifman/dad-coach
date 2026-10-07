package com.dadcoach.web.father;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.web.common.WebException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * מפגשים: the father's sessions, and the two things he may do with one from the web - confirm it happened (with a
 * note) or cancel it - through the SAME QualityTimeService methods the AI tools call (complete_quality_time,
 * cancel_quality_time), so the weekly credit, streak and belt move exactly as they do from WhatsApp. Booking stays
 * in WhatsApp. Platform timers armed for a session are not cleared from here (D-B04): the scheduled turns re-read
 * the weekly plan and stand down for a session that is no longer UPCOMING.
 */
@Service
public class SessionsService {

    /** How far back the "past" list reaches. */
    static final Duration PAST_WINDOW = Duration.ofDays(60);
    static final int NOTE_MAX = 500;

    public record SessionsView(List<SessionView> upcoming, List<SessionView> awaiting, List<SessionView> past,
                               List<HomeView.ChildName> children, String timezone) {
    }

    private final QualityTimeRepository qualityTimes;
    private final QualityTimeService qualityTimeService;
    private final ChildRepository children;
    private final WeeklyGoalService weeklyGoals;
    private final Clock clock;

    public SessionsService(QualityTimeRepository qualityTimes, QualityTimeService qualityTimeService,
                           ChildRepository children, WeeklyGoalService weeklyGoals, Clock clock) {
        this.qualityTimes = qualityTimes;
        this.qualityTimeService = qualityTimeService;
        this.children = children;
        this.weeklyGoals = weeklyGoals;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SessionsView list(Father father) {
        Instant now = clock.instant();
        ZoneId zone = weeklyGoals.zoneFor(father);
        Map<Long, String> names = new HashMap<>();
        List<HomeView.ChildName> childList = new ArrayList<>();
        for (Child child : children.findByFatherIdAndStatus(father.getId(), "ACTIVE")) {
            names.put(child.getId(), child.getName());
            childList.add(new HomeView.ChildName(child.getId(), child.getName()));
        }
        List<SessionView> upcoming = new ArrayList<>();
        List<SessionView> awaiting = new ArrayList<>();
        List<SessionView> past = new ArrayList<>();
        // newest first from the repository
        for (QualityTime qt : qualityTimes.findByFatherIdOrderByScheduledStartDesc(father.getId())) {
            if (qt.getScheduledStart() == null || qt.getScheduledEnd() == null) {
                continue;
            }
            String phase = SessionPhases.phaseOf(qt, now);
            SessionView view = SessionPhases.view(qt, phase, zone, names);
            switch (phase) {
                case "UPCOMING", "IN_PROGRESS" -> upcoming.add(0, view); // soonest first
                case "AWAITING_CONFIRMATION" -> {
                    if (qt.getScheduledEnd().isAfter(now.minus(PAST_WINDOW))) {
                        awaiting.add(view);
                    }
                }
                default -> {
                    if (qt.getScheduledStart().isAfter(now.minus(PAST_WINDOW))) {
                        past.add(view);
                    }
                }
            }
        }
        return new SessionsView(upcoming, awaiting, past, childList, zone.getId());
    }

    /** "It happened": only a session that has started and is still open. */
    @Transactional
    public void confirm(Father father, UUID sessionId, String note) {
        QualityTime qt = owned(father, sessionId);
        String phase = SessionPhases.phaseOf(qt, clock.instant());
        if (!"IN_PROGRESS".equals(phase) && !"AWAITING_CONFIRMATION".equals(phase)) {
            throw WebException.conflict("SESSION_NOT_CONFIRMABLE", "only a started, open session can be confirmed");
        }
        String trimmed = note == null || note.isBlank() ? null : note.strip();
        if (trimmed != null && trimmed.length() > NOTE_MAX) {
            throw WebException.badRequest("NOTE_TOO_LONG", "note too long");
        }
        qualityTimeService.completeQualityTime(qt.getId(), trimmed);
    }

    /** Cancel an upcoming one, or say a past open one did not happen. */
    @Transactional
    public void cancel(Father father, UUID sessionId) {
        QualityTime qt = owned(father, sessionId);
        String phase = SessionPhases.phaseOf(qt, clock.instant());
        if (!"UPCOMING".equals(phase) && !"IN_PROGRESS".equals(phase) && !"AWAITING_CONFIRMATION".equals(phase)) {
            throw WebException.conflict("SESSION_NOT_CANCELLABLE", "only an open session can be cancelled");
        }
        qualityTimeService.cancelQualityTime(qt.getId());
    }

    private QualityTime owned(Father father, UUID sessionId) {
        return qualityTimes.findById(sessionId)
                .filter(qt -> father.getId().equals(qt.getFatherId()))
                .orElseThrow(WebException::notFound);
    }
}
