package com.dadcoach.weeklygoal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.WhatsAppEndpoints;
import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.ProactiveSender;
import com.dadcoach.channel.session.SessionWindowService;
import com.dadcoach.config.BeltImageConfig;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.integration.platform.SentMessageRecorder;
import com.dadcoach.integration.platform.timeline.TimelineReports;
import com.dadcoach.whatsapp.WhatsAppApiClient;
import com.dadcoach.whatsapp.WhatsAppMessageFormatter;
import com.dadcoach.workflow.Belt;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * The belt image is optional: whatever happens to it (Meta refuses it and the client throws, or the send comes back
 * unsuccessful), the promotion text still goes out through the channel layer and is reported as before. It used to be
 * that a thrown image failure skipped the text altogether.
 */
class BeltPromotionNotifierTest {

    static final String IMAGE_URL = "https://cdn.example/yellow.png";

    WhatsAppApiClient api = mock(WhatsAppApiClient.class);
    WhatsAppMessageFormatter formatter = mock(WhatsAppMessageFormatter.class);
    FatherRepository fathers = mock(FatherRepository.class);
    ProactiveSender sender = mock(ProactiveSender.class);
    SentMessageRecorder recorder = mock(SentMessageRecorder.class);
    WhatsAppEndpoints endpoints = mock(WhatsAppEndpoints.class);
    SessionWindowService windows = mock(SessionWindowService.class);
    TimelineReports timeline = mock(TimelineReports.class);
    BeltImageConfig images = new BeltImageConfig();
    Map<String, Object> imagePayload = Map.of("type", "image");

    Father father;
    BeltPromotionNotifier notifier;

    @BeforeEach
    void setUp() {
        father = new Father("+972501234567");
        father.setId(7L);
        images.setYellow(IMAGE_URL);
        CommunicationEndpoint endpoint = mock(CommunicationEndpoint.class);
        when(fathers.findById(7L)).thenReturn(Optional.of(father));
        when(endpoints.ensure(father)).thenReturn(endpoint);
        when(windows.isOpen(endpoint)).thenReturn(true);
        when(formatter.formatImageMessage(eq(father.getPhone()), eq(IMAGE_URL), anyString())).thenReturn(imagePayload);
        when(sender.send(eq(father), anyString()))
                .thenReturn(new ProactiveSender.Outcome(DeliveryResult.sent("wamid.text"), ProactiveSender.Mode.FREE_FORM));
        notifier = new BeltPromotionNotifier(api, formatter, images, fathers, sender, recorder, endpoints, windows);
    }

    void promote() {
        notifier.sendPromotionNotification(new WeeklyGoalService.BeltPromotionResult(7L, true, Belt.WHITE, Belt.YELLOW,
                120, 120, 1, false));
    }

    @Test
    @DisplayName("the image send throws (Meta refused it): the promotion text is still sent and recorded")
    void aThrownImageFailureStillSendsTheText() {
        when(api.sendMessage(imagePayload)).thenThrow(new WhatsAppApiClient.WhatsAppApiException(500, "boom"));

        promote();

        verify(sender).send(eq(father), anyString());
        verify(recorder).recordSent(eq(father), anyString(), eq("belt-promotion:7:YELLOW"));
    }

    @Test
    @DisplayName("the image send comes back unsuccessful: the promotion text is still sent and recorded")
    void anUnsuccessfulImageStillSendsTheText() {
        when(api.sendMessage(imagePayload)).thenReturn(new WhatsAppApiClient.SendResponse(false, null, "refused"));

        promote();

        verify(sender).send(eq(father), anyString());
        verify(recorder).recordSent(eq(father), anyString(), eq("belt-promotion:7:YELLOW"));
    }

    @Test
    @DisplayName("the image send returns nothing (an empty 2xx body): the promotion text is still sent")
    void aMissingImageResponseStillSendsTheText() {
        when(api.sendMessage(imagePayload)).thenReturn(null);

        promote();

        verify(sender).send(eq(father), anyString());
        verify(recorder).recordSent(eq(father), anyString(), eq("belt-promotion:7:YELLOW"));
    }

    @Test
    @DisplayName("the image goes out: image first, then the text, as before")
    void anImageThenTheText() {
        when(api.sendMessage(imagePayload)).thenReturn(new WhatsAppApiClient.SendResponse(true, "wamid.image", null));

        promote();

        InOrder order = inOrder(api, sender, recorder);
        order.verify(api).sendMessage(imagePayload);
        order.verify(sender).send(eq(father), anyString());
        order.verify(recorder).recordSent(eq(father), anyString(), eq("belt-promotion:7:YELLOW"));
    }

    @Test
    @DisplayName("with timeline reports on, a text sent after a failed image is reported to the timeline (not the old record)")
    void aThrownImageFailureIsReportedToTheTimelineWhenOn() {
        when(timeline.enabled()).thenReturn(true);
        notifier.setTimeline(timeline);
        when(api.sendMessage(imagePayload)).thenThrow(new WhatsAppApiClient.RateLimitException(java.time.Duration.ofSeconds(5)));

        promote();

        verify(sender).send(eq(father), anyString());
        verify(timeline).outbound(any(TimelineReports.Person.class), any(TimelineReports.Part.class));
        verify(recorder, never()).recordSent(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("the text itself failing is still not recorded (unchanged)")
    void aFailedTextIsNotRecorded() {
        when(api.sendMessage(imagePayload)).thenThrow(new WhatsAppApiClient.WhatsAppApiException(500, "boom"));
        when(sender.send(eq(father), anyString()))
                .thenReturn(new ProactiveSender.Outcome(DeliveryResult.failed("down"), ProactiveSender.Mode.FREE_FORM));

        promote();

        verify(sender).send(eq(father), anyString());
        verify(recorder, never()).recordSent(any(), anyString(), anyString());
    }
}
