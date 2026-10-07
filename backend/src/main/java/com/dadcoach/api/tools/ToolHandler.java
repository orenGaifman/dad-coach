package com.dadcoach.api.tools;

import java.util.Map;

/**
 * One AI tool bound in the dad-coach-3 workflow. Implementations call the domain services the dashboard uses -
 * never a parallel implementation of a rule. A business rejection is thrown (ApiException, a business-rule or
 * calendar exception); the controller turns it into success:false and rolls back what the tool did.
 */
public interface ToolHandler {

    String toolKey();

    /** Side-effecting tools run once per idempotency key (reserve → execute → replay). */
    boolean sideEffecting();

    Map<String, Object> handle(ToolActor actor, ToolParams params);
}
