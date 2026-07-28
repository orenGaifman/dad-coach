package com.dadcoach.conversation;

import com.dadcoach.conversation.dto.OutboundMessageDto;
import com.dadcoach.conversation.entity.ConversationMessage;
import com.dadcoach.conversation.entity.ProcessedMessage;
import com.dadcoach.conversation.repository.ConversationMessageRepository;
import com.dadcoach.conversation.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService Unit Tests")
class IdempotencyServiceTest {

    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(processedMessageRepository, conversationMessageRepository);
    }

    @Nested
    @DisplayName("checkDuplicate")
    class CheckDuplicate {

        @Test
        @DisplayName("returns empty Optional when idempotency key does not exist")
        void returnsEmpty_whenKeyNotFound() {
            when(processedMessageRepository.findByIdempotencyKey("new-key")).thenReturn(Optional.empty());

            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate("new-key", "+1234567890");

            assertThat(result).isEmpty();
            verifyNoInteractions(conversationMessageRepository);
        }

        @Test
        @DisplayName("returns cached response when duplicate detected with valid response_id")
        void returnsCachedResponse_whenDuplicateWithValidResponseId() {
            UUID responseId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            String recipientId = "+1234567890";

            ProcessedMessage processed = ProcessedMessage.builder()
                    .idempotencyKey("duplicate-key")
                    .fatherId(UUID.randomUUID())
                    .responseId(responseId)
                    .build();

            ConversationMessage outbound = ConversationMessage.builder()
                    .conversationId(conversationId)
                    .direction("OUTBOUND")
                    .content("Cached coaching response")
                    .messageType("TEXT")
                    .metadata(Map.of("model_used", "gpt-4", "cached", true))
                    .sequenceNumber(2)
                    .build();

            when(processedMessageRepository.findByIdempotencyKey("duplicate-key"))
                    .thenReturn(Optional.of(processed));
            when(conversationMessageRepository.findById(responseId))
                    .thenReturn(Optional.of(outbound));

            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate("duplicate-key", recipientId);

            assertThat(result).isPresent();
            OutboundMessageDto dto = result.get();
            assertThat(dto.recipientId()).isEqualTo(recipientId);
            assertThat(dto.content()).isEqualTo("Cached coaching response");
            assertThat(dto.messageType()).isEqualTo("TEXT");
            assertThat(dto.conversationId()).isEqualTo(conversationId);
            assertThat(dto.metadata()).containsEntry("model_used", "gpt-4");
        }

        @Test
        @DisplayName("returns empty when duplicate exists but response_id is null")
        void returnsEmpty_whenResponseIdIsNull() {
            ProcessedMessage processed = ProcessedMessage.builder()
                    .idempotencyKey("key-no-response")
                    .fatherId(UUID.randomUUID())
                    .build();

            when(processedMessageRepository.findByIdempotencyKey("key-no-response"))
                    .thenReturn(Optional.of(processed));

            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate("key-no-response", "+1234567890");

            assertThat(result).isEmpty();
            verifyNoInteractions(conversationMessageRepository);
        }

        @Test
        @DisplayName("returns empty when duplicate exists but outbound message not found")
        void returnsEmpty_whenOutboundMessageNotFound() {
            UUID responseId = UUID.randomUUID();
            ProcessedMessage processed = ProcessedMessage.builder()
                    .idempotencyKey("key-missing-msg")
                    .fatherId(UUID.randomUUID())
                    .responseId(responseId)
                    .build();

            when(processedMessageRepository.findByIdempotencyKey("key-missing-msg"))
                    .thenReturn(Optional.of(processed));
            when(conversationMessageRepository.findById(responseId))
                    .thenReturn(Optional.empty());

            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate("key-missing-msg", "+1234567890");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("uses empty map when outbound message metadata is null")
        void usesEmptyMetadata_whenNullInMessage() {
            UUID responseId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();

            ProcessedMessage processed = ProcessedMessage.builder()
                    .idempotencyKey("key-null-metadata")
                    .fatherId(UUID.randomUUID())
                    .responseId(responseId)
                    .build();

            ConversationMessage outbound = ConversationMessage.builder()
                    .conversationId(conversationId)
                    .direction("OUTBOUND")
                    .content("Response text")
                    .messageType("TEXT")
                    .metadata(null)
                    .sequenceNumber(1)
                    .build();

            when(processedMessageRepository.findByIdempotencyKey("key-null-metadata"))
                    .thenReturn(Optional.of(processed));
            when(conversationMessageRepository.findById(responseId))
                    .thenReturn(Optional.of(outbound));

            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate("key-null-metadata", "+1234567890");

            assertThat(result).isPresent();
            assertThat(result.get().metadata()).isEmpty();
        }
    }

    @Nested
    @DisplayName("recordProcessed")
    class RecordProcessed {

        @Test
        @DisplayName("saves ProcessedMessage with correct fields")
        void savesProcessedMessage_withCorrectFields() {
            UUID fatherId = UUID.randomUUID();
            UUID responseId = UUID.randomUUID();
            String key = "msg-12345";

            idempotencyService.recordProcessed(key, fatherId, responseId);

            ArgumentCaptor<ProcessedMessage> captor = ArgumentCaptor.forClass(ProcessedMessage.class);
            verify(processedMessageRepository).save(captor.capture());

            ProcessedMessage saved = captor.getValue();
            assertThat(saved.getIdempotencyKey()).isEqualTo(key);
            assertThat(saved.getFatherId()).isEqualTo(fatherId);
            assertThat(saved.getResponseId()).isEqualTo(responseId);
            assertThat(saved.getProcessedAt()).isNotNull();
            assertThat(saved.getExpiresAt()).isNotNull();
            // Verify 24-hour TTL (within reasonable tolerance)
            assertThat(saved.getExpiresAt())
                    .isAfter(Instant.now().plusSeconds(23 * 60 * 60))
                    .isBefore(Instant.now().plusSeconds(25 * 60 * 60));
        }

        @Test
        @DisplayName("links response_id to the outbound message produced")
        void linksResponseId_toOutboundMessage() {
            UUID fatherId = UUID.randomUUID();
            UUID responseId = UUID.randomUUID();

            idempotencyService.recordProcessed("key-with-response", fatherId, responseId);

            ArgumentCaptor<ProcessedMessage> captor = ArgumentCaptor.forClass(ProcessedMessage.class);
            verify(processedMessageRepository).save(captor.capture());
            assertThat(captor.getValue().getResponseId()).isEqualTo(responseId);
        }
    }

    @Nested
    @DisplayName("cleanupExpired")
    class CleanupExpired {

        @Test
        @DisplayName("deletes entries where expiresAt is before now")
        void deletesExpiredEntries() {
            when(processedMessageRepository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(5);

            idempotencyService.cleanupExpired();

            ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
            verify(processedMessageRepository).deleteByExpiresAtBefore(captor.capture());
            // The instant should be approximately now
            assertThat(captor.getValue())
                    .isAfter(Instant.now().minusSeconds(5))
                    .isBefore(Instant.now().plusSeconds(5));
        }

        @Test
        @DisplayName("handles case when no expired entries exist")
        void handlesNoExpiredEntries() {
            when(processedMessageRepository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(0);

            idempotencyService.cleanupExpired();

            verify(processedMessageRepository).deleteByExpiresAtBefore(any(Instant.class));
        }
    }
}
