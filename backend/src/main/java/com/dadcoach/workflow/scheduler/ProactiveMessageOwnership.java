package com.dadcoach.workflow.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Who sends proactive (unprompted) coaching messages - session reminders, follow-ups, weekly-goal
 * prompts, nudges - for this deployment ({@code dadcoach.scheduler.proactive-messages-owner}).
 *
 * <ul>
 *   <li>{@code LOCAL} (default): Dad Coach's own scheduled jobs send them (template text). This is
 *       the behavior for workflows that do not schedule their own proactive messages (Dad Coach 2).</li>
 *   <li>{@code PLATFORM}: the Workflow Platform workflow schedules and writes them (Dad Coach 3) and
 *       they arrive through the scheduled-response callback, so the overlapping local jobs stand
 *       down to avoid duplicates. Data-maintenance jobs (weekly goal completion, belts) still run.</li>
 * </ul>
 *
 * <p>The worker key is one value per deployment ({@code workflow.platform.worker-key}), so this is
 * switched together with it.</p>
 */
@Component
public class ProactiveMessageOwnership {

    private static final Logger log = LoggerFactory.getLogger(ProactiveMessageOwnership.class);

    public enum Owner { LOCAL, PLATFORM }

    private final Owner owner;

    public ProactiveMessageOwnership(@Value("${dadcoach.scheduler.proactive-messages-owner:LOCAL}") Owner owner) {
        this.owner = owner;
        log.info("Proactive messages owner: {}", owner);
    }

    public Owner owner() {
        return owner;
    }

    /** True when Dad Coach's own scheduled jobs should send proactive messages. */
    public boolean localSchedulerSends() {
        return owner == Owner.LOCAL;
    }
}
