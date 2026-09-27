package com.dadcoach.channel.delivery;

import com.dadcoach.channel.ChannelAdapter;
import com.dadcoach.channel.ChannelRouter;
import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.channel.capability.ChannelCapabilities;
import com.dadcoach.channel.capability.MessageDowngrader;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.session.SessionWindowService;
import com.dadcoach.channel.template.TemplateMessage;
import com.dadcoach.channel.template.TemplateRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Window/template rules of the channel delivery layer. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveryServiceTemplateTest {

    private static final UUID FATHER = new UUID(0L, 7L);

    @Mock private CommunicationEndpointRepository endpointRepository;
    @Mock private SessionWindowService sessionWindowService;
    @Mock private ChannelRouter channelRouter;
    @Mock private TemplateRegistry templateRegistry;
    @Mock private ChannelAdapter adapter;

    private DeliveryService service() {
        CommunicationEndpoint endpoint = new CommunicationEndpoint(FATHER, "WHATSAPP", "+972501234567");
        when(endpointRepository.findPrimaryByFatherId(FATHER)).thenReturn(Optional.of(endpoint));
        when(sessionWindowService.isOpen(endpoint)).thenReturn(false);
        when(channelRouter.getAdapter("WHATSAPP")).thenReturn(adapter);
        when(adapter.getCapabilities()).thenReturn(ChannelCapabilities.allSupported());
        when(adapter.sendMessage(any(), anyString())).thenReturn(DeliveryResult.sent("wamid.1"));
        return new DeliveryService(endpointRepository, sessionWindowService, channelRouter, new MessageDowngrader(), templateRegistry);
    }

    private static OutboundMessageDto message(boolean template, String templateName) {
        return new OutboundMessageDto(UUID.randomUUID(), FATHER, null, MessageType.TEXT, "hi", null, template,
                templateName, template ? Map.of("1", "hi") : null, MessagePriority.IMMEDIATE, Instant.now());
    }

    @Test
    void freeFormIsRejectedWhenTheSessionIsClosed() {
        DeliveryResult result = service().deliver(message(false, null));

        assertThat(result.failureReason()).isEqualTo(DeliveryService.SESSION_CLOSED);
        verify(adapter, never()).sendMessage(any(), anyString());
    }

    @Test
    void aHebrewTemplateIsApprovedAgainstItsHebrewRegistration() {
        when(templateRegistry.findApprovedTemplate("dad_coach_update_he", "he"))
                .thenReturn(Optional.of(mock(TemplateMessage.class)));

        DeliveryResult result = service().deliver(message(true, "dad_coach_update_he"));

        assertThat(result.isSuccessful()).isTrue();
        verify(adapter).sendMessage(any(), eq("+972501234567"));
    }

    @Test
    void anUnapprovedTemplateIsNotSent() {
        when(templateRegistry.findApprovedTemplate(anyString(), anyString())).thenReturn(Optional.empty());

        DeliveryResult result = service().deliver(message(true, "dad_coach_update_he"));

        assertThat(result.failureReason()).isEqualTo(DeliveryService.TEMPLATE_UNAVAILABLE);
        verify(adapter, never()).sendMessage(any(), anyString());
    }

    @Test
    void templateLanguageFollowsTheNameSuffixConvention() {
        assertThat(TemplateRegistry.languageForTemplateName("dad_coach_update_he")).isEqualTo("he");
        assertThat(TemplateRegistry.languageForTemplateName("dad_coach_update_en")).isEqualTo("en");
        assertThat(TemplateRegistry.languageForTemplateName("legacy_name")).isEqualTo("en");
    }
}
