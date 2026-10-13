package com.dadcoach.integration.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.client.PlatformMessagingClient.Kind;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/** D-045 / D-053 unit: the answer table, the key shape, the settings, and which transport failures provably wrote nothing. */
class PlatformMessagingUnitTest {

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private static PlatformMessagingClient.Answer answer(int status, String body) throws Exception {
        return new PlatformMessagingClient.Answer(Kind.ANSWERED, status, body == null ? null : JSON.readTree(body), null, null);
    }

    @Test
    void theAnswerTableHasNoDirectRow() throws Exception {
        // D-053 (spec 6.4 §4.1): provably nothing created = NOT_CREATED (pending, re-POSTed), never "send it yourself"
        var disabled = answer(503, "{\"error\":\"messaging.disabled\"}");
        assertThat(PlatformSendFlow.verdict(disabled, false)).isEqualTo(PlatformSendFlow.Verdict.NOT_CREATED);
        assertThat(PlatformSendFlow.verdict(disabled, true)).isEqualTo(PlatformSendFlow.Verdict.AMBIGUOUS);
        assertThat(PlatformSendFlow.verdict(answer(401, null), false)).isEqualTo(PlatformSendFlow.Verdict.NOT_CREATED);
        assertThat(PlatformSendFlow.verdict(answer(403, null), false)).isEqualTo(PlatformSendFlow.Verdict.NOT_CREATED);
        var never = new PlatformMessagingClient.Answer(Kind.NEVER_WRITTEN, 0, null, "ConnectException", null);
        assertThat(PlatformSendFlow.verdict(never, false)).isEqualTo(PlatformSendFlow.Verdict.NOT_CREATED);
        assertThat(PlatformSendFlow.verdict(never, true)).isEqualTo(PlatformSendFlow.Verdict.AMBIGUOUS);
        assertThat(PlatformSendFlow.verdict(answer(422, "{\"error\":\"messaging.invalid_request\"}"), false))
                .isEqualTo(PlatformSendFlow.Verdict.REFUSED);
        assertThat(PlatformSendFlow.verdict(answer(503, "{\"error\":\"other\"}"), false))
                .isEqualTo(PlatformSendFlow.Verdict.AMBIGUOUS);
        assertThat(PlatformSendFlow.verdict(answer(200, "{\"messageId\":\"m\",\"status\":\"QUEUED\"}"), false))
                .isEqualTo(PlatformSendFlow.Verdict.OWNED);
        assertThat(PlatformSendFlow.Verdict.values()).extracting(Enum::name).doesNotContain("DIRECT");
    }

    @Test
    void aKeyThePlatformsShapeRefusesIsReplacedDeterministically() {
        // D-053 (spec v2 R5): the platform's key shape is [A-Za-z0-9:_.~@/+=-]{1,200}
        assertThat(PlatformSendFlow.platformKey("reply:wamid.HBgM=+/:0")).isEqualTo("reply:wamid.HBgM=+/:0");
        assertThat(PlatformSendFlow.platformKey("login-link:" + java.util.UUID.randomUUID())).startsWith("login-link:");
        String odd = PlatformSendFlow.platformKey("scheduled-response:trigger with spaces#1");
        assertThat(odd).matches("h:[0-9a-f]{32}").isEqualTo(PlatformSendFlow.platformKey("scheduled-response:trigger with spaces#1"));
        assertThat(PlatformSendFlow.platformKey("x".repeat(201))).matches("h:[0-9a-f]{32}");
        assertThat(PlatformSendFlow.platformKey("reply:a b:0")).isNotEqualTo(PlatformSendFlow.platformKey("reply:a c:0"));
    }

    @Test
    void theNotCreatedRepostWaitIsAtMostTwoSeconds() {
        var settings = new MessagingSettings(true, java.time.Duration.ofMinutes(5), 2000, 12000);
        assertThat(settings.notCreatedRepostWait()).isEqualTo(java.time.Duration.ofSeconds(2));
        settings.setNotCreatedRepostWait(java.time.Duration.ofSeconds(30));
        assertThat(settings.notCreatedRepostWait()).isEqualTo(java.time.Duration.ofSeconds(2));
        settings.setNotCreatedRepostWait(java.time.Duration.ofMillis(300));
        assertThat(settings.notCreatedRepostWait()).isEqualTo(java.time.Duration.ofMillis(300));
        assertThat(settings.isDeliveryStatusEnabled()).isTrue();
    }

    @Test
    void theReconcileWaitIsNeverBelowFiveMinutes() {
        var settings = new MessagingSettings(false, java.time.Duration.ofSeconds(30), 2000, 12000);
        assertThat(settings.reconcileAfter()).isEqualTo(java.time.Duration.ofMinutes(5));
        settings.setReconcileAfter(java.time.Duration.ofMinutes(1));
        assertThat(settings.reconcileAfter()).isEqualTo(java.time.Duration.ofMinutes(5));
        settings.setReconcileAfter(java.time.Duration.ofMinutes(10));
        assertThat(settings.reconcileAfter()).isEqualTo(java.time.Duration.ofMinutes(10));
    }

    @Test
    void theReconcileLookupAndPassAreBounded() {
        var settings = new MessagingSettings(false, java.time.Duration.ofMinutes(5), 2000, 12000);
        assertThat(settings.lookupReadTimeoutMs()).as("D-047: a lookup has no platform wait").isEqualTo(5000);
        assertThat(settings.reconcilePassBudget()).isEqualTo(java.time.Duration.ofSeconds(15));
        settings.setReconcilePassBudget(java.time.Duration.ZERO);
        assertThat(settings.reconcilePassBudget()).as("no budget is never 'no limit'").isEqualTo(java.time.Duration.ofSeconds(15));
    }

    @Test
    void theReplyReadTimeoutIsShort() {
        var settings = new MessagingSettings(false, java.time.Duration.ofMinutes(5), 2000, 12000);
        assertThat(settings.replyReadTimeoutMs()).as("D-048: the father is waiting").isEqualTo(5000);
        assertThat(MessagingSettings.KINDS).containsExactly("LOGIN_LINK", "SCHEDULED", "REPLY", "FIXED_LINE",
                "BUTTON_REPLY", "READY_ANSWER");
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
