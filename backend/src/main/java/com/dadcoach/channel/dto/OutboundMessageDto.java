package com.dadcoach.channel.dto;

/**
 * The WhatsApp buttons a message carries. The outbound message record that held them is gone: the platform's Send API
 * sends every message (D-053).
 */
public final class OutboundMessageDto {

    private OutboundMessageDto() {
    }

    /** One WhatsApp reply button: the id comes back on a tap, the title is what the father sees (up to 20 characters). */
    public record ReplyButton(String id, String title) {
    }

    /**
     * One WhatsApp URL button: {@code label} on the button (up to 20 characters), {@code url} behind it, an optional
     * small {@code footer} under the text (up to 60). It carries no id - nothing comes back on a tap.
     */
    public record LinkButton(String label, String url, String footer) {
        public LinkButton {
            if (label == null || label.isBlank() || url == null || url.isBlank()) {
                throw new IllegalArgumentException("A link button needs a label and a url");
            }
        }

        /** The text with the link on its own line, then the footer. */
        public String asText(String text) {
            StringBuilder b = new StringBuilder(text == null ? "" : text.strip()).append('\n').append(url);
            if (footer != null && !footer.isBlank()) {
                b.append("\n\n").append(footer);
            }
            return b.toString();
        }
    }
}
