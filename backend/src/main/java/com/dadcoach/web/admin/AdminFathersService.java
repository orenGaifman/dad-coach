package com.dadcoach.web.admin;

import com.dadcoach.auth.DashboardPrincipal;
import com.dadcoach.auth.DashboardSession;
import com.dadcoach.auth.SessionService;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherDataPurger;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.integration.platform.lifecycle.PlatformPersonDeletions;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.weeklygoal.WeeklyGoalRepository;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.web.common.WebException;
import com.dadcoach.web.father.HomeService;
import com.dadcoach.web.father.HomeView;
import com.dadcoach.web.father.SessionPhases;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One father in the admin: everything about him, his home exactly as he sees it (view-as, D-105: the SAME
 * HomeService), and the lifecycle - deactivate (PAUSED, sessions revoked) -> typed permanent delete (the existing
 * admin delete: the platform person-deletion outbox + the local purge, in one transaction).
 */
@Service
public class AdminFathersService {

    private static final Logger log = LoggerFactory.getLogger(AdminFathersService.class);

    public record FatherDetail(Profile profile, List<ChildRow> children, List<GoalRow> goals, List<SessionRow> sessions,
                               List<AdminQueries.DeliveryRow> deliveries, List<Map<String, Object>> loginLinks,
                               long liveDashboardSessions, Map<String, Object> deletion, Instant deactivatedAt,
                               String deleteConfirmation) {
    }

    public record Profile(long id, String name, String phone, String status, String timezone, Instant createdAt,
                          Instant lastInteractionAt, String belt, int completed, int streakWeeks, int longestStreakWeeks,
                          boolean calendarConnected, String onboardingState, String workflowState) {
    }

    public record ChildRow(long id, String name, int age, String status) {
    }

    public record GoalRow(String weekStart, int targetHours, int creditedMinutes, String status) {
    }

    public record SessionRow(String id, String childName, String phase, Instant start, Instant end, String notes) {
    }

    private final FatherRepository fathers;
    private final ChildRepository children;
    private final WeeklyGoalRepository goals;
    private final QualityTimeRepository qualityTimes;
    private final WeeklyGoalService weeklyGoals;
    private final HomeService home;
    private final AdminQueries queries;
    private final SessionService sessions;
    private final PlatformPersonDeletions deletions;
    private final FatherDataPurger purger;
    private final Clock clock;

    public AdminFathersService(FatherRepository fathers, ChildRepository children, WeeklyGoalRepository goals,
                               QualityTimeRepository qualityTimes, WeeklyGoalService weeklyGoals, HomeService home,
                               AdminQueries queries, SessionService sessions, PlatformPersonDeletions deletions,
                               FatherDataPurger purger, Clock clock) {
        this.fathers = fathers;
        this.children = children;
        this.goals = goals;
        this.qualityTimes = qualityTimes;
        this.weeklyGoals = weeklyGoals;
        this.home = home;
        this.queries = queries;
        this.sessions = sessions;
        this.deletions = deletions;
        this.purger = purger;
        this.clock = clock;
    }

    public Father find(long fatherId) {
        return fathers.findById(fatherId).orElseThrow(WebException::notFound);
    }

    @Transactional(readOnly = true)
    public FatherDetail detail(long fatherId) {
        Father f = find(fatherId);
        Instant now = clock.instant();
        ZoneId zone = weeklyGoals.zoneFor(f);
        Profile profile = new Profile(f.getId(), f.getDisplayName(), f.getPhone(), f.getStatus().name(), zone.getId(),
                f.getCreatedAt(), f.getLastInteractionAt(), f.getCurrentBelt() == null ? null : f.getCurrentBelt().name(),
                f.getTotalQualityTimesCompleted(), nz(f.getCurrentStreakWeeks()), nz(f.getLongestStreakWeeks()),
                f.hasGoogleCalendarConfigured(), f.getOnboardingState() == null ? null : f.getOnboardingState().name(),
                f.getCurrentWorkflowState() == null ? null : f.getCurrentWorkflowState().name());
        List<ChildRow> kids = children.findByFatherId(fatherId).stream()
                .map(c -> new ChildRow(c.getId(), c.getName(), c.getAge(), c.getStatus())).toList();
        List<GoalRow> goalRows = goals.findByFatherIdOrderByWeekStartDateDesc(fatherId).stream().limit(26)
                .map(g -> new GoalRow(g.getWeekStartDate().toString(), g.getTargetHours(), g.getActualMinutes(), g.getStatus().name()))
                .toList();
        List<SessionRow> sessionRows = qualityTimes.findByFatherIdOrderByScheduledStartDesc(fatherId).stream().limit(40)
                .filter(qt -> qt.getScheduledStart() != null && qt.getScheduledEnd() != null)
                .map(qt -> new SessionRow(qt.getId().toString(), qt.getChild() == null ? null : qt.getChild().getName(),
                        SessionPhases.phaseOf(qt, now), qt.getScheduledStart(), qt.getScheduledEnd(), qt.getCompletionNotes()))
                .toList();
        return new FatherDetail(profile, kids, goalRows, sessionRows, queries.deliveriesOf(fatherId, 30),
                queries.loginLinksOf(fatherId, 10), queries.liveSessionsOf(fatherId, now), queries.deletionOf(fatherId),
                queries.deactivatedAt(fatherId), deleteConfirmation(f));
    }

    /** View-as: the father's home, built by the same service his own dashboard uses. */
    public HomeView homeOf(long fatherId) {
        return home.home(find(fatherId));
    }

    @Transactional
    public void deactivate(long fatherId, DashboardPrincipal by) {
        Father f = find(fatherId);
        if (f.getStatus() == FatherStatus.DELETED) {
            throw WebException.conflict("ALREADY_DELETED", "the father is being deleted");
        }
        if (f.getStatus() != FatherStatus.PAUSED) {
            queries.recordDeactivation(fatherId, f.getStatus().name(), clock.instant(), by.staffUserId());
            f.setStatus(FatherStatus.PAUSED);
            fathers.save(f);
        }
        sessions.revokeAllForFather(fatherId, DashboardSession.REASON_DEACTIVATED);
        log.info("admin.father.deactivated fatherId={}", fatherId);
    }

    @Transactional
    public void reactivate(long fatherId) {
        Father f = find(fatherId);
        if (f.getStatus() != FatherStatus.PAUSED) {
            throw WebException.conflict("NOT_DEACTIVATED", "the father is not deactivated");
        }
        String previous = queries.previousStatus(fatherId);
        f.setStatus(previous == null ? FatherStatus.ACTIVE : FatherStatus.valueOf(previous));
        fathers.save(f);
        queries.clearDeactivation(fatherId);
        log.info("admin.father.reactivated fatherId={}", fatherId);
    }

    /** Permanent: only after deactivation, and only with the typed confirmation (his name, or his phone's last 4). */
    @Transactional
    public void deletePermanently(long fatherId, String confirmation) {
        Father f = find(fatherId);
        if (f.getStatus() != FatherStatus.PAUSED) {
            throw WebException.conflict("DEACTIVATE_FIRST", "deactivate before deleting");
        }
        if (confirmation == null || !deleteConfirmation(f).equals(confirmation.strip())) {
            throw WebException.badRequest("CONFIRMATION_MISMATCH", "the typed confirmation does not match");
        }
        String phone = f.getPhone();
        fathers.flush();
        deletions.request(fatherId, phone, false);
        purger.purge(fatherId);
        log.info("admin.father.deleted fatherId={}", fatherId);
    }

    static String deleteConfirmation(Father f) {
        String name = f.getDisplayName();
        if (name != null && !name.isBlank()) {
            return name.strip();
        }
        String digits = f.getPhone() == null ? "" : f.getPhone().replaceAll("[^0-9]", "");
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
