package com.dadcoach.integration.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.integration.platform.messaging.MessagingSettings.Mode;
import com.dadcoach.integration.platform.messaging.PlatformMessagingClient.Kind;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/** D-045 unit: the switch's values, and which transport failures provably wrote nothing. */
class PlatformMessagingUnitTest {

    @Test
    void onlyTheThreeNamesAreModesAnythingElseIsDirect() {
        assertThat(MessagingSettings.parse("LOGIN_LINK", "platform")).isEqualTo(Mode.PLATFORM);
        assertThat(MessagingSettings.parse("LOGIN_LINK", " Shadow ")).isEqualTo(Mode.SHADOW);
        assertThat(MessagingSettings.parse("LOGIN_LINK", "DIRECT")).isEqualTo(Mode.DIRECT);
        assertThat(MessagingSettings.parse("LOGIN_LINK", "")).isEqualTo(Mode.DIRECT);
        assertThat(MessagingSettings.parse("LOGIN_LINK", null)).isEqualTo(Mode.DIRECT);
        assertThat(MessagingSettings.parse("LOGIN_LINK", "PLATFROM")).isEqualTo(Mode.DIRECT);
        assertThat(new MessagingSettings("", false, java.time.Duration.ofMinutes(5), 2000, 12000).loginLink())
                .isEqualTo(Mode.DIRECT);
    }

    @Test
    void theReconcileWaitIsNeverBelowFiveMinutes() {
        var settings = new MessagingSettings("PLATFORM", false, java.time.Duration.ofSeconds(30), 2000, 12000);
        assertThat(settings.reconcileAfter()).isEqualTo(java.time.Duration.ofMinutes(5));
        settings.setReconcileAfter(java.time.Duration.ofMinutes(1));
        assertThat(settings.reconcileAfter()).isEqualTo(java.time.Duration.ofMinutes(5));
        settings.setReconcileAfter(java.time.Duration.ofMinutes(10));
        assertThat(settings.reconcileAfter()).isEqualTo(java.time.Duration.ofMinutes(10));
    }

    @Test
    void theReconcileLookupAndPassAreBounded() {
        var settings = new MessagingSettings("PLATFORM", false, java.time.Duration.ofMinutes(5), 2000, 12000);
        assertThat(settings.lookupReadTimeoutMs()).as("D-047: a lookup has no platform wait").isEqualTo(5000);
        assertThat(settings.reconcilePassBudget()).isEqualTo(java.time.Duration.ofSeconds(15));
        settings.setReconcilePassBudget(java.time.Duration.ZERO);
        assertThat(settings.reconcilePassBudget()).as("no budget is never 'no limit'").isEqualTo(java.time.Duration.ofSeconds(15));
    }

    @Test
    void theConversationalSwitchesDefaultToDirectAndParseAsTheOthers() {
        var settings = new MessagingSettings("", false, java.time.Duration.ofMinutes(5), 2000, 12000);
        for (String kind : java.util.List.of("REPLY", "FIXED_LINE", "BUTTON_REPLY", "READY_ANSWER")) {
            assertThat(settings.conversational(kind)).as(kind).isEqualTo(Mode.DIRECT);
        }
        settings.setConversationalModes("platform", " Shadow ", "PLATFROM", "");
        assertThat(settings.conversational("REPLY")).isEqualTo(Mode.PLATFORM);
        assertThat(settings.conversational("FIXED_LINE")).isEqualTo(Mode.SHADOW);
        assertThat(settings.conversational("BUTTON_REPLY")).isEqualTo(Mode.DIRECT);
        assertThat(settings.conversational("READY_ANSWER")).isEqualTo(Mode.DIRECT);
        assertThat(settings.conversational("LOGIN_LINK")).as("not a conversational kind").isEqualTo(Mode.DIRECT);
        assertThat(settings.replyReadTimeoutMs()).as("D-048: the father is waiting").isEqualTo(5000);
    }

    private static WebClientRequestException request(Throwable cause) {
        return new WebClientRequestException(cause, HttpMethod.POST, java.net.URI.create("https://p.test/x"), new HttpHeaders());
    }

    @Test
    void refusedDnsAndTlsNeverWroteAnythingEverythingElseMayHave() {
        assertThat(PlatformMessagingClient.failure(request(new ConnectException("refused"))).kind()).isEqualTo(Kind.NEVER_WRITTEN);
        assertThat(PlatformMessagingClient.failure(request(new UnknownHostException("p.test"))).kind()).isEqualTo(Kind.NEVER_WRITTEN);
        assertThat(PlatformMessagingClient.failure(request(new SSLHandshakeException("bad cert"))).kind())
                .isEqualTo(Kind.NEVER_WRITTEN);
        assertThat(PlatformMessagingClient.failure(new TimeoutException()).kind()).isEqualTo(Kind.AMBIGUOUS);
        assertThat(PlatformMessagingClient.failure(new TimeoutException()).error()).isEqualTo("TIMEOUT");
        assertThat(PlatformMessagingClient.failure(request(new IOException("Connection reset by peer"))).kind())
                .isEqualTo(Kind.AMBIGUOUS);
        assertThat(PlatformMessagingClient.failure(new IllegalStateException("x")).kind()).isEqualTo(Kind.AMBIGUOUS);
    }
}
