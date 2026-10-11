package com.dadcoach.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * WhatsApp's text rules for what Dad Coach hands the platform's Send API: markdown passes through and a text message is
 * cut to WhatsApp's 4096 characters. Since D-053 (Unified Workflow 6.4) the platform builds every Meta payload (text,
 * interactive, template); the payload builders that were here went with Dad Coach's own Meta sends.
 */
@Component
public class WhatsAppMessageFormatter {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessageFormatter.class);

    /** WhatsApp text message character limit. */
    public static final int TEXT_CHARACTER_LIMIT = 4096;

    /** WhatsApp's limit on the body of an interactive message. */
    public static final int INTERACTIVE_BODY_LIMIT = 1024;

    /**
     * WhatsApp markdown (*bold*, _italic_, ~strikethrough~, ```monospace```) is expected in the text already; emoji pass
     * through unchanged.
     */
    public String applyWhatsAppMarkdown(String text) {
        return text;
    }

    /** Enforces the WhatsApp text character limit (4096 chars): a longer text is cut with an ellipsis. */
    public String enforceCharacterLimit(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= TEXT_CHARACTER_LIMIT) {
            return text;
        }
        log.warn("Text message exceeds WhatsApp character limit ({}). Truncating.", TEXT_CHARACTER_LIMIT);
        return text.substring(0, TEXT_CHARACTER_LIMIT - 3) + "...";
    }
}
