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
}
