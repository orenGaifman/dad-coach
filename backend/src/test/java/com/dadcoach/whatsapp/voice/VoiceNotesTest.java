package com.dadcoach.whatsapp.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** D-027: when a voice note is heard, and what the admin sees when one is not. */
class VoiceNotesTest {

    private VoiceNoteSettings settings;
    private VoiceNoteProperties properties;
    private WhatsAppMediaDownloader downloader;
    private ElevenLabsTranscriber transcriber;
    private VoiceNotes voiceNotes;

    @BeforeEach
    void setUp() {
        settings = mock(VoiceNoteSettings.class);
        when(settings.enabled()).thenReturn(true);
        properties = new VoiceNoteProperties();
        properties.setElevenlabsApiKey("test-key");
        downloader = mock(WhatsAppMediaDownloader.class);
        transcriber = mock(ElevenLabsTranscriber.class);
        voiceNotes = new VoiceNotes(settings, properties, downloader, transcriber);
    }

    @Test
    void aNoteIsDownloadedAndItsWordsComeBack() {
        when(downloader.download("m1", properties.getMaxBytes())).thenReturn(new WhatsAppMediaDownloader.Media(new byte[] {1, 2}, "audio/ogg"));
        when(transcriber.transcribe(any(), any())).thenReturn("רוצה לקבוע זמן עם נועה");

        assertThat(voiceNotes.listen("m1")).isEqualTo(new VoiceNotes.Outcome.Heard("רוצה לקבוע זמן עם נועה"));
        assertThat(voiceNotes.status().lastHeardAt()).isNotNull();
        assertThat(voiceNotes.status().lastSafeErrorSummary()).isNull();
    }

    @Test
    void offWhenTheAdminTurnedItOffOrNoKeyIsSet() {
        when(settings.enabled()).thenReturn(false);
        assertThat(voiceNotes.active()).isFalse();
        assertThat(voiceNotes.listen("m1")).isEqualTo(new VoiceNotes.Outcome.Off());

        when(settings.enabled()).thenReturn(true);
        properties.setElevenlabsApiKey(" ");
        assertThat(voiceNotes.active()).isFalse();
        assertThat(voiceNotes.listen("m1")).isEqualTo(new VoiceNotes.Outcome.Off());
        assertThat(voiceNotes.status().configured()).isFalse();
        assertThat(voiceNotes.status().enabled()).isTrue();
        verify(downloader, never()).download(any(), anyLong());
    }

    @Test
    void aNoteWithoutWordsIsSilent() {
        when(downloader.download(any(), anyLong())).thenReturn(new WhatsAppMediaDownloader.Media(new byte[] {1}, "audio/ogg"));
        when(transcriber.transcribe(any(), any())).thenReturn("");

        assertThat(voiceNotes.listen("m1")).isEqualTo(new VoiceNotes.Outcome.Silent());
    }

    @Test
    void aNoteOverTheLimitIsTooLongAndNotAFailure() {
        when(downloader.download(any(), anyLong())).thenThrow(VoiceNoteException.tooLong(9_000_000));

        assertThat(voiceNotes.listen("m1")).isEqualTo(new VoiceNotes.Outcome.TooLong());
        assertThat(voiceNotes.status().lastFailedAt()).isNull();
        verify(transcriber, never()).transcribe(any(), any());
    }

    @Test
    void aFailureIsRecordedForTheAdminByItsSafeCode() {
        when(downloader.download(any(), anyLong())).thenReturn(new WhatsAppMediaDownloader.Media(new byte[] {1}, "audio/ogg"));
        when(transcriber.transcribe(any(), any())).thenThrow(new VoiceNoteException("ELEVENLABS_QUOTA_EXCEEDED", "ElevenLabs answered 401"));

        assertThat(voiceNotes.listen("m1")).isEqualTo(new VoiceNotes.Outcome.Failed("ELEVENLABS_QUOTA_EXCEEDED"));
        assertThat(voiceNotes.status().lastFailedAt()).isNotNull();
        assertThat(voiceNotes.status().lastSafeErrorSummary()).isEqualTo("ELEVENLABS_QUOTA_EXCEEDED");
    }

    @Test
    void anUnexpectedErrorIsAFailureNotACrash() {
        when(downloader.download(any(), anyLong())).thenThrow(new NullPointerException());

        assertThat(voiceNotes.listen("m1")).isEqualTo(new VoiceNotes.Outcome.Failed("UNEXPECTED"));
    }
}
