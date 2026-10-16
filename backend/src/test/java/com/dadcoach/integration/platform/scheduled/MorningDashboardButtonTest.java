package com.dadcoach.integration.platform.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dadcoach.auth.LoginLink;
import com.dadcoach.auth.LoginLinkRepository;
import com.dadcoach.auth.LoginLinkService;
import com.dadcoach.auth.SignInPolicy;
import com.dadcoach.auth.SignInSubject;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.template.TemplateCall;
import com.dadcoach.channel.template.TemplateText;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.messaging.PlatformMessagingClient;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.whatsapp.buttons.SessionButtonOffers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * D-067, without a database: the session-day morning's request carries his dashboard button (WhatsApp {@code cta_url},
 * platform {@code link_button}) under the unchanged text, the stored bytes never hold the sign-in token, and the token is
 * put in only for the POST. The end-to-end path (the callback, the link row, the reconcile) is
 * {@code ScheduledPlatformSendTest}'s.
 */
class MorningDashboardButtonTest {

    static final String BASE = "https://dad-coach.example";
    static final String PHONE = "+19995550990";
    static final String MORNING = ScheduledMessageTemplates.MORNING_STATE;
    static final String TEXT = WhatsAppTemplateCatalog.IDENTITY + "*היום ב-15:00 וב-17:00* זה הזמן שלך ושל נועה ויובל 🙂";
    static final Instant NOW = Instant.parse("2026-11-03T06:00:00Z");

    final ObjectMapper json = new ObjectMapper();
    final LoginLinkRepository linkRows = mock(LoginLinkRepository.class);
    final SignInPolicy policy = mock(SignInPolicy.class);
    final FatherRepository fathers = mock(FatherRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    LoginLinkService links;
    ScheduledPlatformSend send;
    Father father;

    @BeforeEach
    void setUp() {
        WeeklyGoalService goals = mock(WeeklyGoalService.class);
        when(goals.zoneFor(any())).thenReturn(ZoneId.of("Asia/Jerusalem"));
        PlatformSendFlow flow = new PlatformSendFlow(mock(MessagingSettings.class), mock(PlatformMessagingClient.class),
                new WorkflowPlatformProperties(), mock(CommunicationEndpointRepository.class), jdbc,
                mock(PlatformTransactionManager.class), json, clock);
        links = new LoginLinkService(linkRows, policy, null, null, clock, BASE + "/");
        send = new ScheduledPlatformSend(flow, mock(ScheduledResponseDeliveryRepository.class),
                mock(ScheduledMessageTemplates.class), fathers, mock(QualityTimeRepository.class),
                mock(SessionButtonOffers.class), goals, new WorkflowPlatformProperties(), links, jdbc, clock);
        father = new Father(PHONE);
        ReflectionTestUtils.setField(father, "id", 42L);
        when(linkRows.save(any(LoginLink.class))).thenAnswer(i -> i.getArgument(0));
    }

    Optional<TemplateCall> own() {
        return Optional.of(TemplateCall.of(WhatsAppTemplateCatalog.SESSION_MORNING_HE, "15:00 וב-17:00", "נועה ויובל"));
    }

    JsonNode morningRequest() throws Exception {
        return json.readTree(send.request(father, "scheduled-response:t-m", "t-m", MORNING, TEXT, List.of(), own(), true));
    }

    @Test
    @DisplayName("the morning is one link_button: the text exactly as today, the dashboard card's label, no footer, the "
            + "fallback = the text with the link on its own line, sensitive; the token is the mark in what is built")
    void morningIsALinkButton() throws Exception {
        JsonNode q = morningRequest();
        JsonNode content = q.path("content");
        String url = BASE + "/auth/consume#token=" + ScheduledPlatformSend.LINK_TOKEN_MARK;
        assertThat(content.path("type").asText()).isEqualTo("link_button");
        assertThat(content.path("text").asText()).isEqualTo(TEXT);
        assertThat(content.path("link").path("title").asText()).isEqualTo("כניסה לדף שלי");
        assertThat(content.path("link").path("url").asText()).isEqualTo(url);
        assertThat(content.has("footer")).as("rule 1: no footer").isFalse();
        assertThat(content.has("buttons")).isFalse();
        assertThat(q.path("fallbackText").asText()).isEqualTo(TEXT + "\n" + url);
        assertThat(q.path("sensitive").asBoolean()).isTrue();
        assertThat(q.path("kind").asText()).isEqualTo("SCHEDULED");
        assertThat(q.path("timeline").path("content").asText()).as("the timeline row: the text, never the link")
                .isEqualTo(TEXT.substring(WhatsAppTemplateCatalog.IDENTITY.length())).doesNotContain("auth/consume");

        JsonNode data = q.path("templateData");
        assertThat(data.path("variant").asText()).isEqualTo("session_morning_he");
        assertThat(data.path("values")).hasSize(2);
        assertThat(data.path("values").get(0).asText()).isEqualTo("15:00 וב-17:00");
        assertThat(data.path("values").get(1).asText()).isEqualTo("נועה ויובל");
        assertThat(data.path("named").path("text").asText()).isEqualTo(TemplateText.asTemplateParameter(TEXT));
        assertThat(data.path("named").path("link").asText()).isEqualTo(ScheduledPlatformSend.LINK_TOKEN_MARK);
        assertThat(data.has("payloads")).isFalse();
    }

    @Test
    @DisplayName("the token goes in only for the POST: the button's URL, the fallback and named.link, nowhere else")
    void theTokenIsPutInForThePost() throws Exception {
        String stored = send.request(father, "scheduled-response:t-m", "t-m", MORNING, TEXT, List.of(), own(), true);
        String token = "Tk3vQ9pLm2RtY7wB4nZc8dHs6fJa1gEu5iKo0qWxTyU";
        String posted = ScheduledPlatformSend.withToken(stored, token);
        assertThat(stored).doesNotContain(token);
        assertThat(posted).doesNotContain(ScheduledPlatformSend.LINK_TOKEN_MARK);
        JsonNode q = json.readTree(posted);
        assertThat(q.path("content").path("link").path("url").asText()).isEqualTo(BASE + "/auth/consume#token=" + token);
        assertThat(q.path("fallbackText").asText()).isEqualTo(TEXT + "\n" + BASE + "/auth/consume#token=" + token);
        assertThat(q.path("templateData").path("named").path("link").asText()).isEqualTo(token);
        assertThat(posted.split(token, -1)).as("three places").hasSize(4);
        assertThat(json.readTree(posted.replace(token, ScheduledPlatformSend.LINK_TOKEN_MARK))).isEqualTo(json.readTree(stored));
    }

    @Test
    @DisplayName("only the session-day morning, without reply buttons, short enough for an interactive body (rule 7)")
    void whoGetsTheButton() throws Exception {
        assertThat(ScheduledPlatformSend.dashboardButton(MORNING, TEXT, List.of())).isTrue();
        for (String other : List.of("SESSION_REMINDER_1H", "SESSION_FOLLOW_UP", "ACTIVE_COACHING", "WEEKLY_PLANNING")) {
            assertThat(ScheduledPlatformSend.dashboardButton(other, TEXT, List.of())).as(other).isFalse();
        }
        assertThat(ScheduledPlatformSend.dashboardButton(MORNING, TEXT,
                List.of(new OutboundMessageDto.ReplyButton("dc:ideas:" + UUID.randomUUID(), "רוצה רעיונות")))).isFalse();
        assertThat(ScheduledPlatformSend.dashboardButton(MORNING, "א".repeat(1025), List.of())).isFalse();

        JsonNode without = json.readTree(send.request(father, "scheduled-response:t-m", "t-m", MORNING, TEXT, List.of(),
                own(), false));
        assertThat(without.path("content").path("type").asText()).as("no link: today's message").isEqualTo("text");
        assertThat(without.has("fallbackText")).isFalse();
        assertThat(without.has("sensitive")).isFalse();
        assertThat(without.path("templateData").path("named").has("link")).isFalse();
    }

    @Test
    @DisplayName("a morning link: valid 7 days, only its hash stored, the URL lands on his home (no next)")
    void theMorningLink() {
        when(policy.forPhone(PHONE)).thenReturn(Optional.of(new SignInSubject(42L, null)));
        LoginLinkService.MorningLink link = links.issueMorning(PHONE).orElseThrow();
        org.mockito.ArgumentCaptor<LoginLink> saved = org.mockito.ArgumentCaptor.forClass(LoginLink.class);
        verify(linkRows).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(link.id());
        assertThat(saved.getValue().getFatherId()).isEqualTo(42L);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(ReflectionTestUtils.getField(saved.getValue(), "tokenHash"))
                .isEqualTo(com.dadcoach.auth.TokenHashing.sha256Hex(link.token())).isNotEqualTo(link.token());
        assertThat(links.buttonUrl(link.token())).isEqualTo(BASE + "/auth/consume#token=" + link.token());

        when(policy.forPhone("+19995550991")).thenReturn(Optional.empty());
        assertThat(links.issueMorning("+19995550991")).as("may not sign in: no link").isEmpty();
    }

    @Test
    @DisplayName("a reconcile re-POST of a morning mints a new link; bytes without the mark are posted as stored; no link "
            + "to issue = never posted again")
    void reconcileRePostMintsAgain() throws Exception {
        UUID row = UUID.randomUUID();
        when(jdbc.queryForObject(any(String.class), eq(Long.class), eq(row))).thenReturn(42L);
        when(fathers.findById(42L)).thenReturn(Optional.of(father));
        when(policy.forPhone(PHONE)).thenReturn(Optional.of(new SignInSubject(42L, null)));
        String stored = send.request(father, "scheduled-response:t-m", "t-m", MORNING, TEXT, List.of(), own(), true);

        String first = send.toPost(row, stored);
        String second = send.toPost(row, stored);
        String t1 = json.readTree(first).path("templateData").path("named").path("link").asText();
        String t2 = json.readTree(second).path("templateData").path("named").path("link").asText();
        assertThat(t1).isNotBlank().isNotEqualTo(t2).isNotEqualTo(ScheduledPlatformSend.LINK_TOKEN_MARK);
        assertThat(first).isEqualTo(ScheduledPlatformSend.withToken(stored, t1));

        String plain = "{\"content\":{\"type\":\"text\",\"text\":\"x\"}}";
        assertThat(send.toPost(row, plain)).isSameAs(plain);

        when(policy.forPhone(PHONE)).thenReturn(Optional.empty());
        assertThat(send.toPost(row, stored)).isNull();
        verify(linkRows, never()).revoke(any(), any(), any());
    }
}
