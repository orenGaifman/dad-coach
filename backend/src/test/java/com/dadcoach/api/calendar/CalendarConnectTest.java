package com.dadcoach.api.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.calendar.CalendarLinkSigner;
import com.dadcoach.domain.father.Father;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Only a signed, unexpired link starts the Google OAuth flow - and only for the father it was signed for. */
class CalendarConnectTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired CalendarLinkSigner signer;

    @Test
    void aSignedLinkRedirectsToGoogleAnUnsignedOrForeignOneDoesNot() throws Exception {
        Father f = data.activeFather("+19995550800");
        Father other = data.activeFather("+19995550801");
        String query = signer.connectQuery(f.getId());
        MvcResult ok = mvc.perform(get("/api/v1/calendar/connect/" + f.getId() + "?" + query)).andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(302);
        assertThat(ok.getResponse().getHeader("Location")).startsWith("https://accounts.google.com/");

        assertThat(mvc.perform(get("/api/v1/calendar/connect/" + other.getId() + "?" + query)).andReturn().getResponse().getStatus())
                .isEqualTo(404);
        assertThat(mvc.perform(get("/api/v1/calendar/connect/" + f.getId())).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void theCallbackEndsOnTheDashboardEvenWithAForgedState() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/calendar/callback").param("code", "c").param("state", "1.9999999999..forged")).andReturn();
        assertThat(r.getResponse().getHeader("Location")).startsWith("https://app.dadcoach.test/").contains("calendar_error=missing_params");
    }
}
