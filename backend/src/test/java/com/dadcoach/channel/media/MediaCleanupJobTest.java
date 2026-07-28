package com.dadcoach.channel.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MediaCleanupJob verifying daily expired asset deletion.
 */
@ExtendWith(MockitoExtension.class)
class MediaCleanupJobTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @InjectMocks
    private MediaCleanupJob mediaCleanupJob;

    @Test
    @DisplayName("cleanup deletes expired assets using current time as cutoff")
    void cleanupExpiredMedia_deletesExpired() {
        when(mediaAssetRepository.deleteExpiredAssets(any(Instant.class))).thenReturn(5);

        mediaCleanupJob.cleanupExpiredMedia();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(mediaAssetRepository).deleteExpiredAssets(cutoffCaptor.capture());

        Instant capturedCutoff = cutoffCaptor.getValue();
        // The cutoff should be approximately now (within 1 second)
        assertTrue(java.time.Duration.between(capturedCutoff, Instant.now()).abs().getSeconds() < 2);
    }

    @Test
    @DisplayName("cleanup handles zero expired assets gracefully")
    void cleanupExpiredMedia_noExpiredAssets() {
        when(mediaAssetRepository.deleteExpiredAssets(any(Instant.class))).thenReturn(0);

        assertDoesNotThrow(() -> mediaCleanupJob.cleanupExpiredMedia());

        verify(mediaAssetRepository).deleteExpiredAssets(any(Instant.class));
    }
}
