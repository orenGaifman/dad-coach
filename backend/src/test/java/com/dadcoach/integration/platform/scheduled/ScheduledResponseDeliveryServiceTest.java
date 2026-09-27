package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.DeliveryService;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.domain.conversation.MessageLogService;
import com.dadcoach.domain.father.Father;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledResponseDeliveryServiceTest {

    private static final String KEY = "scheduled-response:t-1";
    private static final String CONTENT = "עוד שעה הזמן שלכם יחד.\nיש לך כבר רעיון מה תעשו?";

    @Mock
    private ScheduledResponseDeliveryRepository repository;
    @Mock
    private DeliveryService deliveryService;
    @Mock
    private MessageLogService messageLogService;

    private ScheduledResponseCallbackConfig config;
    private ScheduledResponseDeliveryService service;
    private Father father;
    private ScheduledResponseRequest request;

    @BeforeEach
    void setUp() {
        config = new ScheduledResponseCallbackConfig();
        service = new ScheduledResponseDeliveryService(repository, deliveryService, messageLogService, config);
        father = new Father("+972501234567");
        father.setId(7L);
        request = new ScheduledResponseRequest("t-1", "i-1", "whatsapp:+972501234567", "WHATSAPP",
                "SESSION_REMINDER_1H", CONTENT);
    }

    private void newKey() {
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private List<OutboundMessageDto> sentMessages(int expectedCalls) {
        ArgumentCaptor<OutboundMessageDto> captor = ArgumentCaptor.forClass(OutboundMessageDto.class);
        verify(deliveryService, times(expectedCalls)).deliver(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("inside the 24h window the platform's text is sent free-form through the delivery layer and logged")
    void insideWindowSendsFreeForm() {
        newKey();
        when(deliveryService.deliver(any())).thenReturn(DeliveryResult.sent("wamid.1"));

        ScheduledResponseResult result = service.deliver(father, request, KEY);

        OutboundMessageDto sent = sentMessages(1).get(0);
        assertThat(sent.isTemplate()).isFalse();
        assertThat(sent.textContent()).isEqualTo(CONTENT);
        assertThat(sent.fatherId()).isEqualTo(new UUID(0L, 7L));
        verify(messageLogService).logOutbound(7L, CONTENT);
        assertThat(result.status()).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("outside the window the configured approved template is sent, carrying the message as a single-line {{1}}")
    void outsideWindowFallsBackToTemplate() {
        config.setTemplateName("dad_coach_update_he");
        newKey();
        when(deliveryService.deliver(any()))
                .thenReturn(DeliveryResult.rejected(DeliveryService.SESSION_CLOSED))
                .thenReturn(DeliveryResult.sent("wamid.2"));

        ScheduledResponseResult result = service.deliver(father, request, KEY);

        OutboundMessageDto template = sentMessages(2).get(1);
        assertThat(template.isTemplate()).isTrue();
        assertThat(template.templateName()).isEqualTo("dad_coach_update_he");
        assertThat(template.templateParameters())
                .isEqualTo(Map.of("1", "עוד שעה הזמן שלכם יחד. יש לך כבר רעיון מה תעשו?"));
        verify(messageLogService).logOutbound(7L, CONTENT);
        assertThat(result.status()).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("outside the window with no template configured nothing is sent and the delivery is recorded FAILED")
    void outsideWindowWithoutTemplateIsNotSent() {
        newKey();
        when(deliveryService.deliver(any())).thenReturn(DeliveryResult.rejected(DeliveryService.SESSION_CLOSED));

        ScheduledResponseResult result = service.deliver(father, request, KEY);

        assertThat(sentMessages(1)).allMatch(m -> !m.isTemplate());
        verify(messageLogService, never()).logOutbound(any(), anyString());
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.detail()).startsWith("SESSION_CLOSED").contains("no approved template");
    }

    @Test
    @DisplayName("a non-window delivery failure is recorded FAILED without a template attempt")
    void otherFailuresDoNotTriggerTemplate() {
        config.setTemplateName("dad_coach_update_he");
        newKey();
        when(deliveryService.deliver(any())).thenReturn(DeliveryResult.failed("CIRCUIT_BREAKER_OPEN"));

        ScheduledResponseResult result = service.deliver(father, request, KEY);

        sentMessages(1);
        verify(messageLogService, never()).logOutbound(any(), anyString());
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.detail()).isEqualTo("CIRCUIT_BREAKER_OPEN");
    }

    @Test
    @DisplayName("a repeated callback for an already-delivered trigger never sends again")
    void repeatedCallbackReplaysWithoutSending() {
        ScheduledResponseDelivery delivered = new ScheduledResponseDelivery(KEY, "t-1", "i-1", 7L, "SESSION_REMINDER_1H");
        delivered.markDelivered(ScheduledResponseDelivery.Mode.FREE_FORM);
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(delivered));

        ScheduledResponseResult result = service.deliver(father, request, KEY);

        verifyNoInteractions(deliveryService);
        assertThat(result.status()).isEqualTo("DELIVERED");
        assertThat(result.detail()).isEqualTo("Already delivered");
    }

    @Test
    @DisplayName("a concurrent duplicate that loses the UNIQUE-key race replays the winner and never sends")
    void concurrentDuplicateNeverSends() {
        ScheduledResponseDelivery winner = new ScheduledResponseDelivery(KEY, "t-1", "i-1", 7L, "SESSION_REMINDER_1H");
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        ScheduledResponseResult result = service.deliver(father, request, KEY);

        verifyNoInteractions(deliveryService);
        assertThat(result.status()).isEqualTo("SENDING");
    }

    @Test
    void templateParametersAreFlattenedToWhatsAppRules() {
        assertThat(ScheduledResponseDeliveryService.asTemplateParameter("a\n\nb\tc      d  "))
                .isEqualTo("a b c   d");
    }
}
