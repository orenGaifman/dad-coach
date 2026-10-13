package com.dadcoach.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.qualitytime.dto.ScheduleQualityTimeResult;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.FakeServers.Reply;
import com.dadcoach.web.father.SettingsService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Google stops accepting a connected calendar (refresh token expired or revoked): settings stop saying "connected"
 * and ask him to reconnect; reconnecting puts the upcoming sessions booked meanwhile into his calendar.
 */
class CalendarReconnectTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired CalendarLinkSigner signer;
    @Autowired FatherRepository fathers;
    @Autowired QualityTimeService sessions;
    @Autowired QualityTimeRepository qualityTimes;
    @Autowired SettingsService settings;

    private static final Instant WEDNESDAY_4PM_IL = Instant.parse("2026-11-04T14:00:00Z");

    private Father connectedFather() {
        Father f = data.activeFather("+19995550900");
        f.setGoogleCalendarEnabled(true);
        f.setGoogleRefreshToken("old-refresh");
        f.setGoogleAccessToken("old-access");
        f.setGoogleTokenExpiresAt(Instant.EPOCH);
        return fathers.saveAndFlush(f);
    }

    @Test
    @DisplayName("an expired authorization: the session is booked, settings say reconnect instead of connected")
    void anExpiredAuthorizationShowsReconnect() {
        Father f = connectedFather();
        Child noa = data.child(f, "נועה", 7);
        fake.onGoogle(c -> new Reply(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}"));

        ScheduleQualityTimeResult r = sessions.scheduleQualityTime(f.getId(), noa.getId(), WEDNESDAY_4PM_IL, Duration.ofHours(1));

        assertThat(r.calendarEventId()).isNull();
        assertThat(r.calendarError()).isEqualTo("CALENDAR_RECONNECT_REQUIRED");
        assertThat(qualityTimes.findById(r.qualityTimeId())).isPresent();
        Father after = fathers.findById(f.getId()).orElseThrow();
        assertThat(after.hasGoogleCalendarConfigured()).isFalse();
        assertThat(after.calendarNeedsReconnect()).isTrue();
        SettingsService.SettingsView view = settings.view(after);
        assertThat(view.calendarConnected()).isFalse();
        assertThat(view.calendarReconnectRequired()).isTrue();
    }

    @Test
    @DisplayName("reconnecting puts the upcoming sessions without an event into his calendar (not past ones)")
    void reconnectingAddsTheUpcomingSessions() throws Exception {
        Father f = connectedFather();
        Child noa = data.child(f, "נועה", 7);
        fake.onGoogle(c -> new Reply(400, "{\"error\":\"invalid_grant\"}"));
        ScheduleQualityTimeResult upcoming = sessions.scheduleQualityTime(f.getId(), noa.getId(), WEDNESDAY_4PM_IL,
                Duration.ofHours(1));
        ScheduleQualityTimeResult earlier = sessions.scheduleQualityTime(f.getId(), noa.getId(),
                clock.instant().minus(Duration.ofHours(3)), Duration.ofHours(1));
        fake.reset();

        String location = mvc.perform(get("/api/v1/calendar/callback").param("code", "fresh-code")
                .param("state", signer.state(f.getId(), "/settings"))).andReturn().getResponse().getHeader("Location");

        assertThat(location).contains("calendar_connected=true");
        assertThat(fake.googleEventCreates()).hasSize(1);
        assertThat(fake.googleEventCreates().get(0).body()).contains("נועה");
        assertThat(qualityTimes.findById(upcoming.qualityTimeId()).orElseThrow().getGoogleCalendarEventId()).startsWith("evt-");
        assertThat(qualityTimes.findById(earlier.qualityTimeId()).orElseThrow().getGoogleCalendarEventId()).isNull();
        Father after = fathers.findById(f.getId()).orElseThrow();
        assertThat(settings.view(after).calendarConnected()).isTrue();
        assertThat(settings.view(after).calendarReconnectRequired()).isFalse();
    }

    @Test
    @DisplayName("connecting twice adds each upcoming session once (no duplicate events)")
    void connectingTwiceAddsNoDuplicates() throws Exception {
        Father f = data.activeFather("+19995550901");
        Child noa = data.child(f, "נועה", 7);
        ScheduleQualityTimeResult upcoming = sessions.scheduleQualityTime(f.getId(), noa.getId(), WEDNESDAY_4PM_IL,
                Duration.ofHours(1));

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/v1/calendar/callback").param("code", "code-" + i)
                    .param("state", signer.state(f.getId(), "/settings")));
        }

        assertThat(fake.googleEventCreates()).hasSize(1);
        assertThat(qualityTimes.findById(upcoming.qualityTimeId()).orElseThrow().getGoogleCalendarEventId())
                .startsWith("evt-");
    }

    @Test
    @DisplayName("a connected calendar follows booking, moving (cancel + book, as the reschedule tool does) and cancelling")
    void bookingMovingAndCancellingKeepTheCalendarInStep() {
        Father f = data.activeFather("+19995550902");
        f.setGoogleCalendarEnabled(true);
        f.setGoogleRefreshToken("refresh");
        f.setGoogleAccessToken("access");
        f.setGoogleTokenExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        fathers.saveAndFlush(f);
        Child noa = data.child(f, "נועה", 7);

        ScheduleQualityTimeResult booked = sessions.scheduleQualityTime(f.getId(), noa.getId(), WEDNESDAY_4PM_IL,
                Duration.ofHours(1));
        assertThat(booked.calendarEventId()).startsWith("evt-");

        sessions.cancelQualityTime(booked.qualityTimeId());
        ScheduleQualityTimeResult moved = sessions.scheduleQualityTime(f.getId(), noa.getId(),
                WEDNESDAY_4PM_IL.plus(Duration.ofDays(1)), Duration.ofHours(1));
        assertThat(moved.calendarEventId()).startsWith("evt-").isNotEqualTo(booked.calendarEventId());

        sessions.cancelQualityTime(moved.qualityTimeId());

        assertThat(fake.googleEventCreates()).hasSize(2);
        assertThat(fake.calls("/google/calendar/").stream().filter(c -> c.method().equals("DELETE"))
                .map(FakeServers.Call::path).toList())
                .containsExactly("/google/calendar/v3/calendars/primary/events/" + booked.calendarEventId(),
                        "/google/calendar/v3/calendars/primary/events/" + moved.calendarEventId());
        assertThat(qualityTimes.findById(moved.qualityTimeId()).orElseThrow().getGoogleCalendarEventId()).isNull();
    }

    private Father connectedWithLiveToken(String phone) {
        Father f = data.activeFather(phone);
        f.setGoogleCalendarEnabled(true);
        f.setGoogleRefreshToken("refresh");
        f.setGoogleAccessToken("revoked-access");
        f.setGoogleTokenExpiresAt(Instant.now().plus(Duration.ofMinutes(50)));
        return fathers.saveAndFlush(f);
    }

    @Test
    @DisplayName("D-057: the grant was revoked outside Dad Coach (a live token refused): settings say reconnect at once")
    void aRefusedLiveTokenWithADeadGrantShowsReconnectAtOnce() {
        Father f = connectedWithLiveToken("+19995550903");
        Child noa = data.child(f, "נועה", 7);
        fake.onGoogle(c -> c.path().equals("/google/token")
                ? new Reply(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}")
                : "Bearer revoked-access".equals(c.header("Authorization")) ? new Reply(401, "{\"error\":{\"code\":401}}")
                : Reply.json("{\"id\":\"evt-x\"}"));

        ScheduleQualityTimeResult r = sessions.scheduleQualityTime(f.getId(), noa.getId(), WEDNESDAY_4PM_IL, Duration.ofHours(1));

        assertThat(r.calendarEventId()).isNull();
        assertThat(r.calendarError()).isEqualTo("CALENDAR_RECONNECT_REQUIRED");
        assertThat(qualityTimes.findById(r.qualityTimeId())).isPresent();
        Father after = fathers.findById(f.getId()).orElseThrow();
        assertThat(after.calendarNeedsReconnect()).isTrue();
        assertThat(settings.view(after).calendarConnected()).isFalse();
        assertThat(settings.view(after).calendarReconnectRequired()).isTrue();
    }

    @Test
    @DisplayName("D-057: only the access token was refused (the grant lives): a refresh and the event is written")
    void aRefusedLiveTokenWithALiveGrantRecovers() {
        Father f = connectedWithLiveToken("+19995550904");
        Child noa = data.child(f, "נועה", 7);
        fake.onGoogle(c -> c.path().equals("/google/token")
                ? Reply.json("{\"access_token\":\"fresh-access\",\"expires_in\":3600}")
                : "Bearer revoked-access".equals(c.header("Authorization")) ? new Reply(401, "{\"error\":{\"code\":401}}")
                : c.method().equals("POST") ? Reply.json("{\"id\":\"evt-after-refresh\"}")
                : Reply.json("{\"items\":[]}"));

        ScheduleQualityTimeResult r = sessions.scheduleQualityTime(f.getId(), noa.getId(), WEDNESDAY_4PM_IL, Duration.ofHours(1));

        assertThat(r.calendarEventId()).isEqualTo("evt-after-refresh");
        assertThat(r.calendarError()).isNull();
        Father after = fathers.findById(f.getId()).orElseThrow();
        assertThat(after.getGoogleAccessToken()).isEqualTo("fresh-access");
        assertThat(settings.view(after).calendarConnected()).isTrue();
    }

    @Test
    @DisplayName("D-057: a refused live token on reading the calendar is forgotten, so the next call refreshes")
    void aRefusedTokenOnReadIsForgotten(@Autowired GoogleCalendarService calendar) {
        Father f = connectedWithLiveToken("+19995550905");
        fake.onGoogle(c -> new Reply(401, "{\"error\":{\"code\":401}}"));

        assertThat(calendar.getUpcomingEvents(f, WEDNESDAY_4PM_IL, WEDNESDAY_4PM_IL.plus(Duration.ofDays(1)), false)).isEmpty();

        Father after = fathers.findById(f.getId()).orElseThrow();
        assertThat(after.getGoogleAccessToken()).isNull();
        assertThat(after.getGoogleRefreshToken()).as("the grant decides at the next refresh").isEqualTo("refresh");
    }
}
