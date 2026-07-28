package com.dadcoach.api.memory;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.ActorType;
import com.dadcoach.api.error.ResourceNotFoundException;
import com.dadcoach.api.pagination.CursorPageResponse;

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

class AdminMemoryControllerTest {

    private AdminMemoryService adminMemoryService;
    private AdminMemoryController controller;
    private ActorContext adminActor;

    @BeforeEach
    void setUp() {
        adminMemoryService = mock(AdminMemoryService.class);
        controller = new AdminMemoryController(adminMemoryService);
        adminActor = new ActorContext(ActorType.ADMIN, UUID.randomUUID());
    }

    @Test
    void listMemories_returnsAllStatesIncludingArchived() {
        UUID fatherId = UUID.randomUUID();

        AdminMemoryDto activeMemory = new AdminMemoryDto();
        activeMemory.setId(UUID.randomUUID());
        activeMemory.setState("ACTIVE");
        activeMemory.setCategory("PREFERENCE");

        AdminMemoryDto archivedMemory = new AdminMemoryDto();
        archivedMemory.setId(UUID.randomUUID());
        archivedMemory.setState("ARCHIVED");
        archivedMemory.setCategory("IDENTITY_FACT");
        archivedMemory.setArchivedAt(Instant.now());

        CursorPageResponse<AdminMemoryDto> page =
                CursorPageResponse.of(List.of(activeMemory, archivedMemory), null, false);

        when(adminMemoryService.listAllMemories(fatherId, null, null, 20))
                .thenReturn(page);

        ResponseEntity<Map<String, Object>> response =
                controller.listMemories(adminActor, fatherId, null, null, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        @SuppressWarnings("unchecked")
        List<AdminMemoryDto> items = (List<AdminMemoryDto>) body.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(1).getState()).isEqualTo("ARCHIVED");
    }

    @Test
    void listMemories_supportsStateFilter() {
        UUID fatherId = UUID.randomUUID();
        CursorPageResponse<AdminMemoryDto> emptyPage = CursorPageResponse.empty();
        when(adminMemoryService.listAllMemories(fatherId, "ARCHIVED", null, 20))
                .thenReturn(emptyPage);

        controller.listMemories(adminActor, fatherId, "ARCHIVED", null, 20);

        verify(adminMemoryService).listAllMemories(fatherId, "ARCHIVED", null, 20);
    }

    @Test
    void listMemories_capsPageSizeAtMaximum() {
        UUID fatherId = UUID.randomUUID();
        CursorPageResponse<AdminMemoryDto> emptyPage = CursorPageResponse.empty();
        when(adminMemoryService.listAllMemories(eq(fatherId), isNull(), isNull(), eq(100)))
                .thenReturn(emptyPage);

        controller.listMemories(adminActor, fatherId, null, null, 500);

        verify(adminMemoryService).listAllMemories(fatherId, null, null, 100);
    }

    @Test
    void getMemoryDetail_returnsFullMemoryWithConfidenceScore() {
        UUID memoryId = UUID.randomUUID();
        AdminMemoryDto memory = new AdminMemoryDto();
        memory.setId(memoryId);
        memory.setFatherId(UUID.randomUUID());
        memory.setCategory("IDENTITY_FACT");
        memory.setContent("Has 2 children");
        memory.setState("ACTIVE");
        memory.setConfidenceScore(0.92);
        memory.setImportanceScore(8);
        memory.setCreatedAt(Instant.now());

        when(adminMemoryService.getMemoryDetail(memoryId)).thenReturn(Optional.of(memory));

        ResponseEntity<AdminMemoryDto> response = controller.getMemoryDetail(adminActor, memoryId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getConfidenceScore()).isEqualTo(0.92);
        assertThat(response.getBody().getState()).isEqualTo("ACTIVE");
    }

    @Test
    void getMemoryDetail_throwsNotFound_whenMemoryDoesNotExist() {
        UUID memoryId = UUID.randomUUID();
        when(adminMemoryService.getMemoryDetail(memoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getMemoryDetail(adminActor, memoryId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Memory");
    }

    @Test
    void getMemoryAuditHistory_returnsAuditEntries() {
        UUID memoryId = UUID.randomUUID();

        AdminMemoryDto memory = new AdminMemoryDto();
        memory.setId(memoryId);
        when(adminMemoryService.getMemoryDetail(memoryId)).thenReturn(Optional.of(memory));

        MemoryAuditEntryDto entry1 = new MemoryAuditEntryDto();
        entry1.setId(UUID.randomUUID());
        entry1.setMemoryId(memoryId);
        entry1.setOperation("CREATED");
        entry1.setNewState("ACTIVE");
        entry1.setCreatedAt(Instant.now().minusSeconds(3600));

        MemoryAuditEntryDto entry2 = new MemoryAuditEntryDto();
        entry2.setId(UUID.randomUUID());
        entry2.setMemoryId(memoryId);
        entry2.setOperation("STATE_TRANSITION");
        entry2.setPreviousState("ACTIVE");
        entry2.setNewState("ARCHIVED");
        entry2.setCreatedAt(Instant.now());

        when(adminMemoryService.getMemoryAuditHistory(memoryId))
                .thenReturn(List.of(entry2, entry1));

        ResponseEntity<List<MemoryAuditEntryDto>> response =
                controller.getMemoryAuditHistory(adminActor, memoryId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).getOperation()).isEqualTo("STATE_TRANSITION");
    }

    @Test
    void getMemoryAuditHistory_throwsNotFound_whenMemoryDoesNotExist() {
        UUID memoryId = UUID.randomUUID();
        when(adminMemoryService.getMemoryDetail(memoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getMemoryAuditHistory(adminActor, memoryId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Memory");
    }
}
