package com.dadcoach.channel.template;

import java.util.List;

/**
 * A message's own template, ready to send outside the 24-hour window: the catalog name, the values for
 * {{1}}..{{n}} (one line each), and one {@code dc:} payload per quick-reply button, in the catalog's button order.
 */
public record TemplateCall(String name, List<String> values, List<String> buttonPayloads) {

    public TemplateCall {
        WhatsAppTemplateCatalog.Entry entry = WhatsAppTemplateCatalog.require(name);
        values = List.copyOf(values);
        buttonPayloads = buttonPayloads == null ? List.of() : List.copyOf(buttonPayloads);
        if (values.size() != entry.maxVariables()) {
            throw new IllegalArgumentException(name + " takes " + entry.maxVariables() + " values, got " + values.size());
        }
        if (buttonPayloads.size() != entry.quickReplies().size()) {
            throw new IllegalArgumentException(name + " has " + entry.quickReplies().size() + " buttons, got "
                    + buttonPayloads.size() + " payloads");
        }
    }

    public static TemplateCall of(String name, String... values) {
        return new TemplateCall(name, List.of(values), List.of());
    }

    public WhatsAppTemplateCatalog.Entry entry() {
        return WhatsAppTemplateCatalog.require(name);
    }

    /** What the father reads: the template with these values. */
    public String text() {
        return entry().render(values);
    }
}
