package com.dadcoach.integration.platform;

import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.lifecycle.PersonRefs;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Makes a message Dad Coach sent by itself (a belt promotion) part of the father's AI conversation, so his reply
 * to it is understood in context (playbook §10.2, BB D-089). The timezone travels too: it seeds a conversation
 * created by this call. Best effort — a failure costs context, never the send.
 */
@Component
public class SentMessageRecorder {

    private final WorkflowPlatformClient platform;
    private final WorkflowPlatformProperties properties;

    public SentMessageRecorder(WorkflowPlatformClient platform, WorkflowPlatformProperties properties) {
        this.platform = platform;
        this.properties = properties;
    }

    public boolean recordSent(Father father, String text, String correlationId) {
        return platform.recordOutbound(new RecordOutboundRequest(properties.getWorkerKey(),
                PersonRefs.whatsappId(father.getPhone()), "whatsapp", correlationId, text,
                Map.of("timezone", FatherTimezones.of(father).getId()), properties.getTenantId(),
                PersonRefs.of(father.getId()), father.getDisplayName(), properties.getWorkflowKey()));
    }
}
