package com.dadcoach.channel.session;

import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.workflow.scheduler.ProactiveMessageOwnership;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundSessionTrackerTest {

    @Mock private FatherRepository fatherRepository;
    @Mock private CommunicationEndpointRepository endpointRepository;
    @Mock private SessionWindowService sessionWindowService;

    private InboundSessionTracker tracker(ProactiveMessageOwnership.Owner owner) {
        return new InboundSessionTracker(fatherRepository, endpointRepository, sessionWindowService,
                new ProactiveMessageOwnership(owner));
    }

    @Test
    void platformOwnerRecordsTheWindowOnTheFathersPrimaryEndpoint() {
        Father father = new Father("+972501234567");
        father.setId(7L);
        CommunicationEndpoint endpoint = new CommunicationEndpoint(new UUID(0L, 7L), "WHATSAPP", "+972501234567");
        when(fatherRepository.findByPhone("+972501234567")).thenReturn(Optional.of(father));
        when(endpointRepository.findPrimaryByFatherId(new UUID(0L, 7L))).thenReturn(Optional.of(endpoint));

        tracker(ProactiveMessageOwnership.Owner.PLATFORM).onInboundWhatsAppMessage("+972501234567");

        verify(sessionWindowService).onInboundMessage(endpoint);
    }

    @Test
    void localOwnerLeavesTheWindowUntouched() {
        tracker(ProactiveMessageOwnership.Owner.LOCAL).onInboundWhatsAppMessage("+972501234567");

        verifyNoInteractions(fatherRepository, endpointRepository, sessionWindowService);
    }

    @Test
    void unknownSenderOrLookupFailureNeverBreaksMessageHandling() {
        when(fatherRepository.findByPhone("+972500000000")).thenReturn(Optional.empty());
        when(fatherRepository.findByPhone("+972599999999")).thenThrow(new RuntimeException("db down"));
        InboundSessionTracker tracker = tracker(ProactiveMessageOwnership.Owner.PLATFORM);

        tracker.onInboundWhatsAppMessage("+972500000000");
        tracker.onInboundWhatsAppMessage("+972599999999");

        verify(sessionWindowService, never()).onInboundMessage(any());
    }
}
