package com.dadcoach.api.conversation;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.ActorType;
import com.dadcoach.api.auth.RolePermission;
import com.dadcoach.api.error.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConversationControllerTest {

    private ConversationService conversationService;
    private ConversationController controller;
    private ActorContext fatherActor;
    private UUID fatherId;

    @BeforeEach
    void setUp() {
        conversationService = mock(ConversationService.class);
        controller = new ConversationController(conversationService);
        fatherId = UUID.randomUUID();
        fatherActor = new ActorContext(ActorType.FATHER, fatherId);
    }

    @Test
    void listConversations_returnsPagedResults() {
        ConversationResponseDto dto = new ConversationResponseDto();
        dto.setId(UUID.randomUUID());
        dto.setType("COACHING");
        dto.setStatus("COMPLETED");
        dto.setMessageCount(5);
        dto.setCreatedAt(Instant.now());

        var page = new ConversationService.ConversationPage(
                List.of(dto), "next_cursor_token", true);

        when(conversationService.listConversations(fatherId, null, 20)).thenReturn(page);

        ResponseEntity<Map<String, Object>> response = controller.listConversations(fatherActor, null, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("has_more")).isEqualTo(true);
        assertThat(body.get("next_cursor")).isEqualTo("next_cursor_token");

        @SuppressWarnings("unchecked")
        List<ConversationResponseDto> items = (List<ConversationResponseDto>) body.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getType()).isEqualTo("COACHING");
    }

    @Test
    void listConversations_capsPageSizeAtMaximum() {
        var emptyPage = new ConversationService.ConversationPage(List.of(), null, false);
        when(conversationService.listConversations(eq(fatherId), isNull(), eq(100))).thenReturn(emptyPage);

        controller.listConversations(fatherActor, null, 200);

        verify(conversationService).listConversations(fatherId, null, 100);
    }

    @Test
    void listConversations_enforceMinimumPageSize() {
        var emptyPage = new ConversationService.ConversationPage(List.of(), null, false);
        when(conversationService.listConversations(eq(fatherId), isNull(), eq(1))).thenReturn(emptyPage);

        controller.listConversations(fatherActor, null, 0);

        verify(conversationService).listConversations(fatherId, null, 1);
    }

    @Test
    void getConversation_returnsConversationWithMessages() {
        UUID conversationId = UUID.randomUUID();

        when(conversationService.getConversationOwnerId(conversationId))
                .thenReturn(Optional.of(fatherId));

        ConversationResponseDto dto = new ConversationResponseDto();
        dto.setId(conversationId);
        dto.setType("COACHING");
        dto.setStatus("ACTIVE");

        ConversationResponseDto.MessageDto msg = new ConversationResponseDto.MessageDto();
        msg.setId(UUID.randomUUID());
        msg.setDirection("OUTBOUND");
        msg.setContent("Hello!");
        msg.setMessageType("TEXT");
        msg.setSequenceNumber(1);
        dto.setMessages(List.of(msg));

        when(conversationService.getConversationWithMessages(conversationId))
                .thenReturn(Optional.of(dto));

        ResponseEntity<ConversationResponseDto> response = controller.getConversation(fatherActor, conversationId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessages()).hasSize(1);
        assertThat(response.getBody().getMessages().get(0).getContent()).isEqualTo("Hello!");
    }

    @Test
    void getConversation_throwsNotFound_whenConversationDoesNotExist() {
        UUID conversationId = UUID.randomUUID();
        when(conversationService.getConversationOwnerId(conversationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getConversation(fatherActor, conversationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Conversation");
    }

    @Test
    void getConversation_throwsNotFound_whenFatherDoesNotOwnConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID otherFatherId = UUID.randomUUID();

        when(conversationService.getConversationOwnerId(conversationId))
                .thenReturn(Optional.of(otherFatherId));

        // RolePermission.assertOwnership throws ResourceNotOwnedException for non-owner fathers
        assertThatThrownBy(() -> controller.getConversation(fatherActor, conversationId))
                .isInstanceOf(RolePermission.ResourceNotOwnedException.class);
    }

    @Test
    void getConversation_allowsAdmin_toAccessAnyConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID otherFatherId = UUID.randomUUID();
        ActorContext adminActor = new ActorContext(ActorType.ADMIN, UUID.randomUUID());

        when(conversationService.getConversationOwnerId(conversationId))
                .thenReturn(Optional.of(otherFatherId));

        ConversationResponseDto dto = new ConversationResponseDto();
        dto.setId(conversationId);
        dto.setType("COACHING");
        dto.setStatus("ACTIVE");
        dto.setMessages(List.of());

        when(conversationService.getConversationWithMessages(conversationId))
                .thenReturn(Optional.of(dto));

        ResponseEntity<ConversationResponseDto> response = controller.getConversation(adminActor, conversationId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
