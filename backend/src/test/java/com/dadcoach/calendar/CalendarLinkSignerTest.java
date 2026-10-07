package com.dadcoach.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A calendar connection can only be started and finished for the father the link was signed for. */
class CalendarLinkSignerTest {

    private static final String WEB = "https://app.example";
    private final Instant now = Instant.parse("2026-10-07T12:00:00Z");
    private final CalendarLinkSigner signer = signer(now);

    private static CalendarLinkSigner signer(Instant at) {
        return new CalendarLinkSigner("a-private-secret-of-more-than-thirty-two-characters", WEB,
                Optional.of(Clock.fixed(at, ZoneOffset.UTC)));
    }

    @Test
    @DisplayName("connect link: valid for its father only, refused when altered, unsigned or expired")
    void connectLink() {
        String q = signer.connectQuery(42);
        long exp = Long.parseLong(q.substring(4, q.indexOf('&')));
        String sig = q.substring(q.indexOf("sig=") + 4);

        assertThat(signer.verifyConnect(42, exp, sig)).isTrue();
        assertThat(signer.verifyConnect(43, exp, sig)).as("another father's id").isFalse();
        assertThat(signer.verifyConnect(42, exp + 3600, sig)).as("extended expiry").isFalse();
        assertThat(signer.verifyConnect(42, null, null)).as("the old unsigned link").isFalse();
        assertThat(signer(now.plus(CalendarLinkSigner.TTL).plusSeconds(1)).verifyConnect(42, exp, sig)).as("expired").isFalse();
    }

    @Test
    @DisplayName("OAuth state: round-trips the father and a web-app path; a forged or plain id state is refused")
    void state() {
        String state = signer.state(42, "/calendar?from=whatsapp");
        assertThat(signer.verifyState(state)).contains(new CalendarLinkSigner.State(42, WEB + "/calendar?from=whatsapp"));
        assertThat(signer.verifyState(signer.state(42, null))).contains(new CalendarLinkSigner.State(42, WEB + "/dashboard"));

        assertThat(signer.verifyState("42")).as("the old plain-id state").isEmpty();
        assertThat(signer.verifyState("42|https://evil.example")).isEmpty();
        assertThat(signer.verifyState(state.replaceFirst("^42\\.", "43."))).as("swapped father id").isEmpty();
        assertThat(signer(now.plus(CalendarLinkSigner.TTL).plusSeconds(1)).verifyState(state)).as("expired").isEmpty();
    }

    @Test
    @DisplayName("the redirect after OAuth never leaves the web app")
    void noOpenRedirect() {
        assertThat(signer.safePath("https://evil.example/x")).isEmpty();
        assertThat(signer.safePath("//evil.example")).isEmpty();
        assertThat(signer.safePath("/\\evil.example")).isEmpty();
        assertThat(signer.safePath(WEB + "@evil.example/")).isEmpty();
        assertThat(signer.safePath(WEB + "/growth")).isEqualTo("/growth");
        assertThat(signer.safePath("/growth")).isEqualTo("/growth");
    }
}
