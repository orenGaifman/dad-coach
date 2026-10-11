package com.dadcoach.weeklygoal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.channel.delivery.ProactiveSender;
import com.dadcoach.config.BeltImageConfig;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.channel.DeliveryStatusController;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.messaging.MessagingSettings.Mode;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow;
import com.dadcoach.integration.platform.timeline.TimelineReports;
import com.dadcoach.support.FakeServers;
import com.dadcoach.support.FakeServers.Reply;
import com.dadcoach.workflow.Belt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * D-046 (Unified Workflow Phase 6.3-DC, DC3): the belt promotion (optional image, then the text) through the platform's
 * Send API, against a fake platform + fake Meta (FakeServers). No real WhatsApp. (The weekly job promotes no one since
 * D-030; the notifier is called directly, as {@code PlatformDeliveryReportsTest} does.)
 */
class BeltPromotionPlatformTest extends AbstractIntegrationTest {

    static final String PHONE = "+19995550960";
    static final String IMAGE = "https://cdn.example/yellow.png";
    static final Reply EDGE_502 = new Reply(502, "<html><body>Bad Gateway</body></html>", "text/html", null);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MessagingSettings messaging;
    @Autowired WorkflowPlatformProperties platformProperties;
    @Autowired TimelineReports timelineReports;
    @Autowired BeltPromotionNotifier belts;
    @Autowired BeltImageConfig beltImages;
    @Autowired PlatformSendFlow flow;

    Father father;

    @BeforeEach
    void setUp() {
        father = data.father(PHONE, "אורן", com.dadcoach.father.FatherStatus.ACTIVE);
        data.endpoint(father, true);
        beltImages.setYellow(IMAGE);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        beltImages.setYellow("");
        for (int i = 0; i < 100 && !flow.shadowIdle(); i++) {
            Thread.sleep(50);
        }
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(10))).isTrue();
    }

    void promote() {
        belts.sendPromotionNotification(new WeeklyGoalService.BeltPromotionResult(father.getId(), true, Belt.WHITE,
                Belt.YELLOW, 120, 120, 1, false));
    }

    JsonNode body(FakeServers.Call call) throws Exception {
        return json.readTree(call.body());
    }

    static Reply accepted(String status) {
        return Reply.json("{\"messageId\":\"" + UUID.randomUUID() + "\",\"status\":\"" + status
                + "\",\"providerMessageId\":\"wamid.p\",\"channelWindow\":\"SESSION\"}");
    }

    @Test
    @DisplayName("DIRECT (default): image then text to Meta, the Send API never asked, the text reported as today")
    void directIsTodaysPath() throws Exception {
        platformProperties.setDeliveryReports(true);
        assertThat(messaging.beltPromotion()).isEqualTo(Mode.DIRECT);
        promote();
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(fake.platformSends()).isEmpty();
        assertThat(fake.metaSends()).hasSize(2);
        assertThat(body(fake.metaSends().get(0)).path("type").asText()).isEqualTo("image");
        assertThat(fake.recordedOutbound()).hasSize(1);
    }

    @Test
    @DisplayName("DIRECT and SHADOW send Meta the same bytes; SHADOW dry-runs the image and the text off the job's thread")
    void directAndShadowSendTheSameBytes() throws Exception {
        promote();
        List<String> direct = fake.metaSends().stream().map(FakeServers.Call::body).toList();

        fake.reset();
        messaging.setBeltPromotion(Mode.SHADOW);
        fake.onPlatformSend(c -> Reply.json("{\"wouldBe\":\"SEND\",\"channelWindow\":\"SESSION\",\"template\":null}"));
        promote();
        for (int i = 0; i < 100 && !(flow.shadowIdle() && fake.platformSends().size() == 2); i++) {
            Thread.sleep(50);
        }
        assertThat(fake.metaSends().stream().map(FakeServers.Call::body).toList()).isEqualTo(direct);
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(body(fake.platformSends().get(0)).path("dryRun").asBoolean()).isTrue();
        assertThat(body(fake.platformSends().get(0)).path("content").path("type").asText()).isEqualTo("image");
        assertThat(body(fake.platformSends().get(1)).path("content").path("type").asText()).isEqualTo("text");
    }

    @Test
    @DisplayName("PLATFORM: the image (SESSION_ONLY, not on the timeline) then the text (general template, NEW_ROW); "
            + "Dad Coach calls neither Meta nor the gate and reports nothing")
    void platformSendsImageThenText() throws Exception {
        messaging.setBeltPromotion(Mode.PLATFORM);
        platformProperties.setDeliveryReports(true);
        fake.onPlatformSend(c -> accepted("ACCEPTED"));

        promote();

        assertThat(fake.metaSends()).isEmpty();
        assertThat(fake.calls("/api/v1/worker/whatsapp/outbound-gate")).isEmpty();
        assertThat(fake.platformSends()).hasSize(2);
        JsonNode image = body(fake.platformSends().get(0));
        assertThat(image.path("idempotencyKey").asText()).isEqualTo("belt-promotion:" + father.getId() + ":YELLOW:image");
        assertThat(image.path("kind").asText()).isEqualTo("BELT_PROMOTION");
        assertThat(image.path("content").path("type").asText()).isEqualTo("image");
        assertThat(image.path("content").path("image").path("link").asText()).isEqualTo(IMAGE);
        assertThat(image.path("content").path("image").path("caption").asText()).contains("💪");
        assertThat(image.path("windowPolicy").asText()).isEqualTo("SESSION_ONLY");
        assertThat(image.has("template")).isFalse();
        assertThat(image.path("timeline").path("record").asBoolean()).isFalse();
        assertThat(image.path("windowHint").path("lastInboundAt").asText()).isEqualTo("2026-11-03T09:00:00Z");

        JsonNode text = body(fake.platformSends().get(1));
        assertThat(text.path("idempotencyKey").asText()).isEqualTo("belt-promotion:" + father.getId() + ":YELLOW:text");
        assertThat(text.path("content").path("type").asText()).isEqualTo("text");
        String message = text.path("content").path("text").asText();
        assertThat(message).startsWith("❤️ דאד קואץ׳:\n💪");
        assertThat(text.path("template").path("name").asText()).isEqualTo(TEMPLATE);
        assertThat(text.path("template").path("bodyParams").get(0).asText()).isEqualTo(ProactiveSender.asTemplateParameter(message));
        assertThat(text.path("windowPolicy").asText()).isEqualTo("AUTO");
        assertThat(text.path("timeline").path("mode").asText()).isEqualTo("NEW_ROW");
        assertThat(text.path("timeline").path("correlationId").asText()).isEqualTo("belt-promotion:" + father.getId() + ":YELLOW");
        assertThat(text.path("timeline").path("content").asText()).startsWith("💪");
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(fake.recordedOutbound()).as("the platform records it").isEmpty();

        promote();
        assertThat(fake.platformSends()).as("a re-run asks again with the same keys (the platform replays them)").hasSize(4);
        assertThat(fake.platformSends().get(2).body()).isEqualTo(fake.platformSends().get(0).body());
    }

    @Test
    @DisplayName("PLATFORM: an image refused (400, e.g. a host off the platform's list) never stops the text, never goes directly")
    void refusedImageNeverStopsTheText() throws Exception {
        messaging.setBeltPromotion(Mode.PLATFORM);
        fake.onPlatformSend(c -> c.body().contains("\"type\":\"image\"")
                ? new Reply(400, "{\"error\":\"messaging.invalid_request\",\"field\":\"content.image.link\"}")
                : accepted("ACCEPTED"));
        promote();
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("PLATFORM 503 messaging.disabled: both parts go today's way (image in his window, then the text, reported)")
    void disabledSendsBothDirect() throws Exception {
        messaging.setBeltPromotion(Mode.PLATFORM);
        platformProperties.setDeliveryReports(true);   // the fake platform is inert by default
        promote();
        assertThat(fake.platformSends()).hasSize(2);
        assertThat(fake.metaSends()).hasSize(2);
        assertThat(body(fake.metaSends().get(0)).path("type").asText()).isEqualTo("image");
        assertThat(timelineReports.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(fake.recordedOutbound()).hasSize(1);
        assertThat(body(fake.recordedOutbound().get(0)).path("kind").asText()).isEqualTo("BELT_PROMOTION");
    }

    @Test
    @DisplayName("PLATFORM ambiguous (edge 502): one identical re-POST per part, never sent directly")
    void ambiguousIsNeverSentDirectly() throws Exception {
        messaging.setBeltPromotion(Mode.PLATFORM);
        fake.onPlatformSend(c -> EDGE_502);
        promote();
        assertThat(fake.platformSends()).hasSize(4);
        assertThat(fake.platformSends().get(1).body()).isEqualTo(fake.platformSends().get(0).body());
        assertThat(fake.platformSends().get(3).body()).isEqualTo(fake.platformSends().get(2).body());
        assertThat(fake.metaSends()).isEmpty();
    }

    @Test
    @DisplayName("A delivery report for a belt promotion (no ledger) is answered known:false and changes nothing")
    void reportsAreUnknown() throws Exception {
        messaging.setDeliveryStatusEnabled(true);
        String messageId = UUID.randomUUID().toString();
        String report = json.writeValueAsString(Map.of("messageId", messageId, "idempotencyKey",
                "belt-promotion:" + father.getId() + ":YELLOW:text", "kind", "BELT_PROMOTION", "status", "DELIVERED"));
        JsonNode a = json.readTree(mvc.perform(post(DeliveryStatusController.PATH).contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", CALLBACK_KEY).header("X-Idempotency-Key", "delivery:" + messageId + ":DELIVERED")
                .content(report)).andReturn().getResponse().getContentAsString());
        assertThat(a.path("known").asBoolean()).isFalse();
        assertThat(a.path("applied").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("PLATFORM with no image configured: the text alone")
    void noImageNoImageRequest() throws Exception {
        beltImages.setYellow("");
        messaging.setBeltPromotion(Mode.PLATFORM);
        AtomicInteger n = new AtomicInteger();
        fake.onPlatformSend(c -> {
            n.incrementAndGet();
            return accepted("QUEUED");
        });
        promote();
        assertThat(n.get()).isEqualTo(1);
        assertThat(body(fake.platformSends().get(0)).path("content").path("type").asText()).isEqualTo("text");
    }
}
