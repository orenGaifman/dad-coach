package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.PlatformUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledResponseControllerTest {

    @Mock
    private PlatformUserResolver userResolver;
    @Mock
    private ScheduledResponseDeliveryService deliveryService;

    private ScheduledResponseController controller;

    @BeforeEach
    void setUp() {
        controller = new ScheduledResponseController(userResolver, deliveryService);
    }

    private static ScheduledResponseRequest request(String content) {
        return new ScheduledResponseRequest("t-1", "i-1", "whatsapp:+972501234567", "WHATSAPP", "ACTIVE_COACHING", content);
    }

    @Test
    void deliversToTheResolvedFatherWithTheTriggerScopedIdempotencyKey() {
        Father father = new Father("+972501234567");
        when(userResolver.resolve("whatsapp:+972501234567")).thenReturn(Optional.of(father));
        when(deliveryService.deliver(eq(father), any(), eq("scheduled-response:t-1")))
                .thenReturn(new ScheduledResponseResult("DELIVERED", "Delivered"));

        ResponseEntity<ScheduledResponseResult> response = controller.handle("scheduled-response:t-1", request("נו, איך היה לכם?"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().status()).isEqualTo("DELIVERED");
    }

    @Test
    void rejectsAMismatchedIdempotencyKeyWithoutDelivering() {
        ResponseEntity<ScheduledResponseResult> response = controller.handle("scheduled-response:other", request("hi"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(deliveryService);
    }

    @Test
    void neverSendsAnEmptyMessage() {
        ResponseEntity<ScheduledResponseResult> response = controller.handle("scheduled-response:t-1", request("  "));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(deliveryService);
    }

    @Test
    void unknownRecipientIsRejectedWithoutDelivering() {
        when(userResolver.resolve(anyString())).thenReturn(Optional.empty());

        ResponseEntity<ScheduledResponseResult> response = controller.handle("scheduled-response:t-1", request("hi"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(deliveryService, never()).deliver(any(), any(), anyString());
    }
}
