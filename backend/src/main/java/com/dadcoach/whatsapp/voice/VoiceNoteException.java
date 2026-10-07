package com.dadcoach.whatsapp.voice;

/** A voice note that could not be heard. {@code code} is safe to show the admin: never a key, the audio or the words. */
public class VoiceNoteException extends RuntimeException {

    private final String code;
    private final boolean tooLong;

    public VoiceNoteException(String code, String message) {
        this(code, message, false);
    }

    private VoiceNoteException(String code, String message, boolean tooLong) {
        super(message);
        this.code = code;
        this.tooLong = tooLong;
    }

    public static VoiceNoteException tooLong(long bytes) {
        return new VoiceNoteException("TOO_LONG", "Voice note of " + bytes + " bytes is over the limit", true);
    }

    public String code() {
        return code;
    }

    public boolean isTooLong() {
        return tooLong;
    }
}
