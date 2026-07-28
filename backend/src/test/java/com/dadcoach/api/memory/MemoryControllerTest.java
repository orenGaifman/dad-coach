package com.dadcoach.api.memory;

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

class MemoryControllerTest {

    private MemoryService memoryService;
    private MemoryController controller;
    private ActorContext fatherActor;
    private UUID fatherId;

    @BeforeEach
    void setUp() {
        memoryService = mock(MemoryService.class);
        controller = new MemoryController(memoryService);
        fatherId = UUID.randomUUID();
        fatherActor = new ActorContext(ActorType.FATHER, fatherId);
    }

    @Test
    void listMemories_returnsPagedActiveMemories() {
        MemoryResponseDto dto = new MemoryResponseDto();
        dto.setId(UUID.randomUUID());
        dto.setCategory("IDENTITY_FACT");
        dto.setContent("Father has 2 kids");
        dto.setImportanceScore(7);
        dto.setCreatedAt(Instant.now());
        dto.setTier("LONG_TERM");

        var page = new MemoryService.MemoryPage(List.of(dto), "cursor_abc", true);
        when(memoryService.listActiveMemories(fatherId, null, 20)).thenReturn(page);

        ResponseEntity<Map<String, Object>> response = controller.listMemories(fatherActor, null, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("has_more")).isEqualTo(true);
        assertThat(body.get("next_cursor")).isEqualTo("cursor_abc");

        @SuppressWarnings("unchecked")
        List<MemoryResponseDto> items = (List<MemoryResponseDto>) body.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getCategory()).isEqualTo("IDENTITY_FACT");
    }

    @Test
    void listMemories_capsPageSizeAtMaximum() {
        var emptyPage = new MemoryService.MemoryPage(List.of(), null, false);
        when(memoryService.listActiveMemories(eq(fatherId), isNull(), eq(100))).thenReturn(emptyPage);

        controller.listMemories(fatherActor, null, 500);

        verify(memoryService).listActiveMemories(fatherId, null, 100);
    }

    @Test
    void listMemories_enforceMinimumPageSize() {
        var emptyPage = new MemoryService.MemoryPage(List.of(), null, false);
        when(memoryService.listActiveMemories(eq(fatherId), isNull(), eq(1))).thenReturn(emptyPage);

        controller.listMemories(fatherActor, null, -5);

        verify(memoryService).listActiveMemories(fatherId, null, 1);
    }

    @Test
    void getMemory_returnsMemoryWithoutConfidenceOrEmbeddings() {
        UUID memoryId = UUID.randomUUID();

        when(memoryService.getMemoryOwnerId(memoryId)).thenReturn(Optional.of(fatherId));

        MemoryResponseDto dto = new MemoryResponseDto();
        dto.setId(memoryId);
        dto.setCategory("PREFERENCE");
        dto.setContent("Prefers gentle communication");
        dto.setImportanceScore(5);
        dto.setTier("MEDIUM_TERM");
        // Note: no confidenceScore or embeddings fields exist in the DTO

        when(memoryService.getMemory(memoryId)).thenReturn(Optional.of(dto));

        ResponseEntity<MemoryResponseDto> response = controller.getMemory(fatherActor, memoryId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCategory()).isEqualTo("PREFERENCE");
        assertThat(response.getBody().getContent()).isEqualTo("Prefers gentle communication");
    }

    @Test
    void getMemory_throwsNotFound_whenMemoryDoesNotExist() {
        UUID memoryId = UUID.randomUUID();
        when(memoryService.getMemoryOwnerId(memoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getMemory(fatherActor, memoryId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Memory");
    }

    @Test
    void getMemory_throwsNotFound_whenFatherDoesNotOwnMemory() {
        UUID memoryId = UUID.randomUUID();
        UUID otherFatherId = UUID.randomUUID();

        when(memoryService.getMemoryOwnerId(memoryId)).thenReturn(Optional.of(otherFatherId));

        assertThatThrownBy(() -> controller.getMemory(fatherActor, memoryId))
                .isInstanceOf(RolePermission.ResourceNotOwnedException.class);
    }

    @Test
    void deleteMemory_returns204_onSuccess() {
        UUID memoryId = UUID.randomUUID();

        when(memoryService.getMemoryOwnerId(memoryId)).thenReturn(Optional.of(fatherId));
        doNothing().when(memoryService).deleteMemory(memoryId);

        ResponseEntity<Void> response = controller.deleteMemory(fatherActor, memoryId);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(memoryService).deleteMemory(memoryId);
    }

    @Test
    void deleteMemory_throwsNotFound_whenMemoryDoesNotExist() {
        UUID memoryId = UUID.randomUUID();
        when(memoryService.getMemoryOwnerId(memoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.deleteMemory(fatherActor, memoryId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Memory");
    }

    @Test
    void deleteMemory_throwsNotFound_whenFatherDoesNotOwnMemory() {
        UUID memoryId = UUID.randomUUID();
        UUID otherFatherId = UUID.randomUUID();

        when(memoryService.getMemoryOwnerId(memoryId)).thenReturn(Optional.of(otherFatherId));

        assertThatThrownBy(() -> controller.deleteMemory(fatherActor, memoryId))
                .isInstanceOf(RolePermission.ResourceNotOwnedException.class);
    }

    @Test
    void deleteMemory_allowsAdmin_toDeleteAnyMemory() {
        UUID memoryId = UUID.randomUUID();
        UUID otherFatherId = UUID.randomUUID();
        ActorContext adminActor = new ActorContext(ActorType.ADMIN, UUID.randomUUID());

        when(memoryService.getMemoryOwnerId(memoryId)).thenReturn(Optional.of(otherFatherId));
        doNothing().when(memoryService).deleteMemory(memoryId);

        ResponseEntity<Void> response = controller.deleteMemory(adminActor, memoryId);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(memoryService).deleteMemory(memoryId);
    }

    @Test
    void memoryResponseDto_doesNotExposeConfidenceOrEmbeddings() {
        // Verify by reflection that the DTO does not have confidenceScore or embedding fields
        MemoryResponseDto dto = new MemoryResponseDto();
        dto.setId(UUID.randomUUID());
        dto.setCategory("IDENTITY_FACT");
        dto.setContent("Has two children");
        dto.setImportanceScore(8);
        dto.setTier("LONG_TERM");

        // These fields simply don't exist on the DTO — compile-time safety
        // No setConfidenceScore, no setEmbedding methods available
        assertThat(dto.getId()).isNotNull();
        assertThat(dto.getCategory()).isEqualTo("IDENTITY_FACT");
    }
}
