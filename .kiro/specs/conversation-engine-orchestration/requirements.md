# Requirements Document

## Introduction

**SPEC-005: Conversation Engine & Orchestration**

This specification defines the complete conversation orchestration layer for the Dad Coach application. It is the authoritative product definition for how inbound messages are processed, how coaching conversations are coordinated from receipt to completion, how context is assembled, how AI interactions are orchestrated, and how asynchronous side-effects are scheduled.

This document defines ONLY business orchestration — the coordination of domain entities, AI interactions, memory operations, and conversation state management. It is intentionally independent of any transport protocol, messaging platform, database technology, framework, or infrastructure concern.

**Scope boundaries:**
- SPEC-001 defines infrastructure and deployment
- SPEC-002 defines domain entities, state machines, and business rules
- SPEC-003 defines AI prompt assembly, model routing, and output contracts
- SPEC-004 defines memory lifecycle, storage, and retrieval
- SPEC-005 (this document) defines how these subsystems are orchestrated together during a coaching interaction

**AI decision boundary:** Consistent with SPEC-003 Requirement 14 and SPEC-004 Requirement 25, the orchestration layer is deterministic. AI components return structured recommendations; the orchestration layer validates and executes all state transitions, entity mutations, and side-effect scheduling.

**Orchestrator-only principle:** The Conversation_Engine is strictly an orchestrator. It coordinates subsystems but does not own or implement their internal logic:
- **Intelligence_Layer (SPEC-003)**: Owns prompt assembly, model routing, response generation, and output schema definition. The Conversation_Engine invokes it and validates results.
- **Memory_System (SPEC-004)**: Owns memory retrieval, extraction, scoring, lifecycle, and consolidation. The Conversation_Engine triggers operations and consumes results.
- **Mission_Service (SPEC-002 Req 6)**: Owns mission generation logic, difficulty rules, and category selection. The Conversation_Engine requests missions and persists validated results.
- **Father_Domain (SPEC-002)**: Owns father profiles, child entities, goals, habits, and engagement metrics. The Conversation_Engine reads domain state and applies validated state transitions.
- **Event_Publisher**: Receives business events from the Conversation_Engine after state transitions succeed. Delivery semantics are owned by the publisher, not the orchestrator.

The Conversation_Engine owns ONLY: pipeline coordination, conversation state management, timing enforcement, concurrency control, and side-effect scheduling.

**Pipeline immutability:** The orchestration pipeline defined in Requirement 3 is the canonical execution order. Any modification to step ordering, addition of new steps, or removal of existing steps requires a revision of this specification.

## Glossary

- **Conversation_Engine**: The orchestration subsystem that coordinates all coaching interactions from message receipt through response delivery and side-effect scheduling
- **Orchestration_Pipeline**: The ordered sequence of steps executed for every inbound message: receive → validate → load context → determine action → generate response → validate output → persist state → schedule side-effects
- **Inbound_Message**: A message received from a father, regardless of transport mechanism
- **Outbound_Message**: A message to be delivered to a father, produced by the orchestration pipeline
- **Conversation**: A bounded sequence of exchanges between father and system with a defined type, objective, and expiration (as defined in SPEC-002)
- **Conversation_Context**: The assembled set of information (father profile, children, goals, missions, memories, history) provided to the AI for response generation
- **Context_Budget**: The maximum information size allocated to the AI context window, partitioned into sections (as defined in SPEC-003)
- **Session_Lock**: A per-father mutual exclusion mechanism ensuring at most one orchestration pipeline executes concurrently for a given father
- **Side_Effect**: An asynchronous operation triggered after the synchronous pipeline completes (memory extraction, metric updates, event publication)
- **Fallback_Response**: A pre-written message delivered when the AI is unavailable or produces invalid output
- **Business_Event**: A domain-significant occurrence published by the orchestration layer for consumption by other subsystems
- **Idempotency_Key**: A unique identifier for each inbound message enabling duplicate detection
- **Recovery_Point**: A persisted checkpoint within the pipeline enabling resumption after partial failure
- **Conversation_Window**: The time period during which a conversation remains ACTIVE and accepts new messages

---

## Requirements

### Requirement 1: Conversation Lifecycle

**User Story:** As a product owner, I want every conversation to follow the state machine defined in SPEC-002 with clear orchestration rules for each transition, so that conversation state is always consistent and predictable.

#### Acceptance Criteria

1. THE Conversation_Engine SHALL enforce the Conversation state machine defined in SPEC-002 Requirement 11 criteria 4:
   - ACTIVE → COMPLETED (objective met or max messages reached)
   - ACTIVE → EXPIRED (expiration time reached without completion)
   - ACTIVE → ABANDONED (father unresponsive for 48 hours)
   No additional states or transitions are introduced by this specification.

2. WHEN a new conversation is created, THE Conversation_Engine SHALL assign:
   - A unique conversation identifier
   - The conversation type (per SPEC-002 Requirement 8 criteria 1)
   - An expiration timestamp based on conversation type (ONBOARDING: 48h, DAILY_COACHING: 24h, FOLLOW_UP: 24h, REFLECTION: 24h, INACTIVITY_CHECK: 48h, CELEBRATION: 24h, DIFFICULT_SITUATION: no expiration)
   - A message counter initialized to 0
   - The owning father identifier

3. THE Conversation_Engine SHALL enforce exactly one ACTIVE conversation per father at any time (per SPEC-002 Requirement 8 criteria 2). If a new conversation must be created while one is ACTIVE:
   - If the new conversation is type DIFFICULT_SITUATION: close the current conversation as COMPLETED (partial), then create the new one
   - Otherwise: queue the new conversation trigger and process it after the current conversation reaches a terminal state

4. WHEN the Conversation_Engine determines a conversation is complete (objective met), it SHALL:
   - Transition the conversation to COMPLETED state
   - Persist the final message count and duration
   - Schedule asynchronous memory extraction (per SPEC-004 Requirement 3)
   - Publish a CONVERSATION_COMPLETED business event

5. WHEN a conversation's expiration timestamp is reached while in ACTIVE state, THE Conversation_Engine SHALL:
   - Transition the conversation to EXPIRED state
   - If the conversation has at least 2 father messages: schedule asynchronous memory extraction on the partial transcript
   - Publish a CONVERSATION_EXPIRED business event
   - Enforce the 24-hour cooldown before initiating a new conversation (per SPEC-002 Requirement 12 criteria 2)

6. WHEN a father has not responded to an ACTIVE conversation for 48 hours, THE Conversation_Engine SHALL transition the conversation to ABANDONED state, request partial summary generation from the Memory_System, and publish a CONVERSATION_ABANDONED business event

7. THE Conversation_Engine SHALL track per conversation: message_count (father messages + system messages), father_message_count, system_message_count, created_at, last_message_at, and completed_at

8. THE Conversation_Engine SHALL enforce the maximum of 8 outbound messages per conversation (per SPEC-002 Requirement 10 criteria 3). When this limit is reached, the conversation transitions to COMPLETED regardless of objective status. Note: This is the hard system cap. Individual conversation types may close earlier per their behavioral rules (e.g., DAILY_COACHING targets 5 exchanges per SPEC-002 Requirement 5 criteria 5). The 8-message limit is the absolute boundary that the Conversation_Engine enforces mechanically.


---

### Requirement 2: Message Lifecycle

**User Story:** As a product owner, I want every inbound message processed reliably with ordering guarantees and duplicate protection, so that no message is lost, duplicated, or processed out of order.

#### Acceptance Criteria

1. WHEN an Inbound_Message arrives, THE Conversation_Engine SHALL process it through the following ordered phases: (1) Accept, (2) Deduplicate, (3) Validate, (4) Route, (5) Orchestrate, (6) Respond, (7) Side-effects

2. THE Conversation_Engine SHALL assign or accept an Idempotency_Key for every Inbound_Message. If a message with the same Idempotency_Key has already been successfully processed, THE Conversation_Engine SHALL return the previously generated response without re-executing the pipeline.

3. THE Conversation_Engine SHALL validate every Inbound_Message for:
   - Non-empty content (at least 1 character of text or a supported media type)
   - Identifiable sender (resolvable to a Father entity or triggering registration)
   - Content length within acceptable bounds (maximum 4096 characters)
   If validation fails, THE Conversation_Engine SHALL discard the message and log the rejection reason without responding to the father.

4. WHEN multiple Inbound_Messages arrive from the same father within a short window (< 5 seconds), THE Conversation_Engine SHALL process them sequentially in receipt order, never concurrently

5. THE Conversation_Engine SHALL process each Inbound_Message exactly once (at-least-once delivery with idempotent handling). If processing fails mid-pipeline and is retried, the system SHALL produce the same outcome as if it had succeeded on the first attempt.

6. WHEN an Inbound_Message arrives from an unknown sender (no matching Father record), THE Conversation_Engine SHALL create a new Father record with status NOT_STARTED and initiate the Onboarding_Flow (per SPEC-002 Requirement 1 criteria 1)

7. THE Conversation_Engine SHALL complete the synchronous portion of the pipeline (through response delivery) within the 30-second latency budget defined in SPEC-002 Requirement 10 criteria 11

8. WHEN the Conversation_Engine cannot complete processing within the latency budget (e.g., AI timeout), it SHALL deliver a Fallback_Response to the father and schedule a retry of the AI generation as an asynchronous side-effect

---

### Requirement 3: Conversation Orchestration

**User Story:** As a product owner, I want every coaching interaction orchestrated through a consistent pipeline that loads the right context, determines the right action, and produces a personalized response, so that coaching feels coherent and contextually aware.

#### Acceptance Criteria

1. WHEN an Inbound_Message is routed to an ACTIVE conversation, THE Conversation_Engine SHALL execute the orchestration pipeline in this order:
   - Step 1: Acquire Session_Lock for the father
   - Step 2: Load Father profile (status, coaching_phase, preferences, engagement_score)
   - Step 3: Load active children (profiles, computed ages, interests)
   - Step 4: Load active goals (descriptions, progress, priorities)
   - Step 5: Load active missions (status, assignment date, target child)
   - Step 6: Load relevant memories (via Memory_System ranked retrieval)
   - Step 7: Load conversation history (current conversation messages)
   - Step 8: Assemble Conversation_Context (per Requirement 4)
   - Step 9: Determine coaching action (per SPEC-003 Decision_Engine)
   - Step 10: Generate AI response (per SPEC-003 prompt assembly and model routing)
   - Step 11: Validate AI output (per Requirement 5)
   - Step 12: Apply business rule validation (per Requirement 5)
   - Step 13: Persist conversation state (new messages, updated counters)
   - Step 14: Deliver Outbound_Message
   - Step 15: Release Session_Lock
   - Step 16: Schedule asynchronous side-effects (per Requirement 7)

2. WHEN no ACTIVE conversation exists for the father and an Inbound_Message arrives, THE Conversation_Engine SHALL determine whether to create a new conversation based on:
   - Father status (must be ACTIVE, ONBOARDING, or REACTIVATED)
   - Time since last conversation ended (must respect 24-hour cooldown if last conversation was EXPIRED, per SPEC-002 Requirement 12 criteria 2)
   - Safety classification of the message (DIFFICULT_SITUATION overrides cooldown)

3. WHEN continuing a multi-turn conversation, THE Conversation_Engine SHALL include the full conversation history (all prior exchanges in the current conversation) in the context, subject to the token budget constraints defined in SPEC-003 Requirement 5

4. THE Conversation_Engine SHALL evaluate conversation completion after every exchange by checking:
   - Has the conversation objective been met? (type-specific criteria per SPEC-002 Requirement 8)
   - Has the maximum message count (8 outbound) been reached?
   - Has the father indicated desire to end the conversation?
   If any condition is true, transition to COMPLETED.

5. WHEN a conversation reaches a terminal state (COMPLETED, EXPIRED, ABANDONED) and a queued conversation trigger exists, THE Conversation_Engine SHALL evaluate and create the queued conversation within the same processing cycle if conditions are met

6. THE Conversation_Engine SHALL handle context switches within a conversation: if the father references a different child than the current mission targets, the pipeline SHALL reload context for the mentioned child, respond appropriately, and preserve the original conversation objective (per SPEC-002 Requirement 12 criteria 4)

7. THE orchestration pipeline defined in criteria 1 is the canonical execution order for this specification. Steps SHALL NOT be reordered, skipped, or extended without a revision of this specification. Individual steps may fail and trigger recovery (Requirement 8), but the defined order is immutable at the product level.

---

### Requirement 4: Context Construction

**User Story:** As a product owner, I want precise rules for what information enters the AI context and in what priority, so that responses are relevant, personalized, and within token limits.

#### Acceptance Criteria

1. THE Conversation_Engine SHALL orchestrate context construction by requesting data from each owning subsystem, then delegating final prompt assembly to the Intelligence_Layer (SPEC-003 Requirement 3). The Conversation_Engine determines WHICH data to request and in what priority; the Intelligence_Layer owns HOW to format and assemble the prompt within its token budget.

2. THE Conversation_Engine SHALL request the following data in this priority order for inclusion in the AI context:
   - Father profile (from Father_Domain): status, coaching_phase, preferences, engagement_score
   - Memories (from Memory_System): ranked retrieval results with the current conversation topic and target child as query parameters
   - Structured context (from Father_Domain + Mission_Service): active goals, active missions, metrics
   - Conversation history (from conversation state): current conversation messages
   The Intelligence_Layer assembles these into the final prompt respecting its token budget (per SPEC-003 Requirement 5 criteria 1).

3. WHEN requesting memories for context, THE Conversation_Engine SHALL provide to the Memory_System:
   - The current conversation topic (derived from the latest father message)
   - The target child (if the conversation involves a specific child)
   - Maximum count and diversity constraints (per SPEC-004 Requirement 16)
   The Memory_System owns the ranking logic, filtering, and diversity enforcement. The Conversation_Engine receives ranked results.

4. THE Conversation_Engine SHALL include in the structured context request:
   - Active goals: primary goal always included; secondary goal included if budget permits
   - Active missions: current mission for the relevant child always included
   - Father metrics: engagement_score, coaching_streak, current phase day count
   - Temporal context: day of week, time of day, weekend indicator
   - Upcoming events: EVENT memories with dates within 3 days (per SPEC-004 Requirement 13 criteria 7)

5. WHEN conversation history exceeds the Intelligence_Layer's token budget for that section, THE Intelligence_Layer applies progressive summarization (per SPEC-003 Requirement 5 criteria 3). The Conversation_Engine provides full history; the Intelligence_Layer truncates.

6. THE Conversation_Engine SHALL record which memories were included in the final assembled prompt (Injected access level per SPEC-004 Requirement 16 criteria 4), enabling the Memory_System to update lifecycle metadata

7. THE Conversation_Engine SHALL communicate priority guidance to the Intelligence_Layer when context must be truncated: (1) system instructions — never truncated, (2) current user message — never truncated, (3) last assistant response — never truncated, (4) active mission context, (5) memories, (6) remaining conversation history, (7) secondary goals

8. WHEN the conversation type is ONBOARDING, THE Conversation_Engine SHALL signal to the Intelligence_Layer that the memories section should be replaced with onboarding-specific context (collected data so far, current onboarding step) since few or no memories exist yet (per SPEC-003 Requirement 3 criteria 10)


---

### Requirement 5: AI Orchestration

**User Story:** As a product owner, I want the AI interaction fully orchestrated with validation, retry, and fallback at every step, so that the father always receives a valid response regardless of AI behavior.

#### Acceptance Criteria

1. THE Conversation_Engine SHALL orchestrate AI interactions through this pipeline:
   - Step 1: Safety classification of the inbound message (per SPEC-003 Requirement 9)
   - Step 2: If SAFE → proceed to coaching response generation
   - Step 3: If non-SAFE classification → apply safety protocol (per SPEC-003 Requirement 9 criteria 3-8) and skip normal response generation
   - Step 4: Assemble prompt from Conversation_Context (per SPEC-003 Requirement 3)
   - Step 5: Route to appropriate AI model (per SPEC-003 Requirement 10)
   - Step 6: Receive AI output
   - Step 7: Validate output against the structured schema for the interaction type (per SPEC-003 Requirement 15)
   - Step 8: Validate output against business rules (language matches father's preference (English or Hebrew), length within bounds, no forbidden patterns, no PII leakage)
   - Step 9: If validation passes → accept the response
   - Step 10: If validation fails → retry once with correction instructions appended
   - Step 11: If retry also fails → use Fallback_Response

2. THE Conversation_Engine SHALL enforce the AI retry policy:
   - Maximum 1 retry per AI generation attempt (2 total calls maximum for a single response)
   - Retry uses the same model with a correction instruction appended
   - If the primary model fails entirely (timeout, error): fall through the Fallback_Chain defined in SPEC-003 Requirement 10 criteria 3
   - Total AI orchestration must complete within the 30-second latency budget

3. THE Conversation_Engine SHALL validate every AI-generated coaching response for:
   - Schema compliance: all required fields present per SPEC-003 Requirement 15 output contracts
   - Language: response text is in the father's preferred language (English or Hebrew, from language_preferences)
   - Length: word count within bounds for the conversation type (per SPEC-003 Requirement 3 criteria 8)
   - Safety: no forbidden patterns detected (per SPEC-003 Requirement 2 criteria 9)
   - Relevance: response references at least one element from the injected context (a memory, mission, or child name)
   - Confidentiality: no PII from other fathers present

4. WHEN the AI returns a suggested_follow_up_action in the CoachingResponse (per SPEC-003 Requirement 15 criteria 1), THE Conversation_Engine SHALL evaluate whether to execute it:
   - NONE: no follow-up action
   - ASK_QUESTION: continue the conversation (no state change)
   - GENERATE_MISSION: queue mission generation as a side-effect (per Requirement 6)
   - SCHEDULE_REFLECTION: queue a REFLECTION conversation trigger for Sunday
   - CLOSE_CONVERSATION: transition conversation to COMPLETED
   The orchestration layer decides; the AI only recommends.

5. WHEN the AI is completely unavailable (all Fallback_Chain attempts exhausted), THE Conversation_Engine SHALL:
   - Deliver the pre-written Fallback_Response (per SPEC-002 Requirement 10 criteria 14)
   - Persist the father's message in conversation history (it is not lost)
   - Create an operations alert
   - Schedule a deferred AI generation attempt as a side-effect (retry in 5 minutes)
   - NOT transition the conversation to a terminal state (the conversation remains ACTIVE)

6. THE Conversation_Engine SHALL track AI orchestration metadata per request: model_used, latency, token_usage, validation_result, retry_count, fallback_used (per SPEC-003 Requirement 16)

---

### Requirement 6: Mission Orchestration

**User Story:** As a product owner, I want mission state changes fully coordinated with conversation flow, so that missions progress naturally through coaching interactions without inconsistency.

#### Acceptance Criteria

1. WHEN the Decision_Engine recommends GENERATE_MISSION or GENERATE_EASIER_MISSION, THE Conversation_Engine SHALL:
   - Request a mission from the Mission_Planner (SPEC-003 Requirement 7), providing the required context (child, phase, recent history)
   - The Mission_Planner owns generation logic (category selection, difficulty calculation, child targeting). The Conversation_Engine only invokes and validates the result.
   - Validate the returned recommendation against SPEC-002 Requirement 6 constraints (title length, category, difficulty within phase bounds, no active mission for target child)
   - If valid: persist the new Mission entity in ASSIGNED state and deliver it as part of the coaching message
   - If invalid: request regeneration once; if still invalid, skip mission delivery and log the failure

2. WHEN a father's message indicates mission completion (explicit statement or clear implication), THE Conversation_Engine SHALL:
   - Validate the completion claim (per SPEC-002 Requirement 12 criteria 3 — verify if < 5 minutes for difficulty 3+)
   - If verification needed: ask one verification question before persisting completion
   - If valid: transition the mission to COMPLETED, prompt for outcome_rating and reflection
   - Schedule mission-outcome memory extraction as a side-effect

3. WHEN a father's message indicates mission acceptance, THE Conversation_Engine SHALL transition the mission from ASSIGNED to ACCEPTED

4. WHEN a father's message indicates mission skip ("I can't", "not today", "skip"), THE Conversation_Engine SHALL transition the mission to SKIPPED and acknowledge the decision without judgment

5. THE Conversation_Engine SHALL detect mission expiration during context loading: if an ASSIGNED or ACCEPTED mission has passed its deadline, transition it to EXPIRED before proceeding with the orchestration pipeline

6. WHEN a mission is completed with outcome_rating, THE Conversation_Engine SHALL evaluate difficulty adjustment rules (per SPEC-002 Requirement 6 criteria 16-17) and store the adjustment for the next mission generation

7. THE Conversation_Engine SHALL never create more than 1 active mission per child (per SPEC-002 Requirement 6 criteria 15). If a mission generation is triggered but an active mission already exists for the target child, the generation is skipped.

---

### Requirement 7: Memory Orchestration

**User Story:** As a product owner, I want memory operations triggered at the right moments and executed asynchronously, so that the memory system stays current without adding latency to conversations.

#### Acceptance Criteria

1. THE Conversation_Engine SHALL trigger memory extraction asynchronously after:
   - A conversation transitions to COMPLETED (full extraction per SPEC-004 Requirement 3 criteria 1)
   - A conversation transitions to EXPIRED with at least 2 father messages (partial extraction per SPEC-004 Requirement 3 criteria 2)
   Memory extraction SHALL NOT block the conversation response (per SPEC-004 Requirement 20 criteria 1).

2. THE Conversation_Engine SHALL trigger memory confirmation when:
   - The AI references a memory in its response AND the father responds affirmatively in the next message
   - Detection of confirmation is performed after the father's response is received, as part of the next pipeline execution
   Confirmation processing is synchronous (lightweight metadata update).

3. THE Conversation_Engine SHALL trigger memory supersession when:
   - The father uses correction language and the corrected fact matches an existing memory
   - Supersession is processed synchronously within the pipeline (the correction must be reflected in the current conversation's context)

4. THE Conversation_Engine SHALL record which memories were Injected into each AI prompt, enabling the Memory_System to update access metadata (per SPEC-004 Requirement 16 criteria 4)

5. THE Conversation_Engine SHALL record which memories were Referenced by the AI in the delivered response (detected by comparing response content against injected memory content), enabling the Memory_System to track Referenced access events

6. WHEN memory extraction fails (AI unavailable, validation failure), THE Conversation_Engine SHALL:
   - Log the failure
   - Preserve the conversation transcript for deferred extraction
   - Queue a retry per SPEC-004 Requirement 20 criteria 8
   - NOT impact the father's experience (the conversation has already been responded to)

7. THE Conversation_Engine SHALL request Conversation Summary Memory creation from the Memory_System (per SPEC-004 Requirement 14 criteria 2) as part of the memory extraction side-effect after conversation completion. The Memory_System owns summary generation logic; the Conversation_Engine only triggers the request and provides the conversation transcript.

---

### Requirement 8: Conversation Recovery

**User Story:** As a product owner, I want the system to recover gracefully from any failure mid-conversation, so that fathers never experience data loss or inconsistent state.

#### Acceptance Criteria

1. THE Conversation_Engine SHALL define the following Recovery_Points within the pipeline:
   - RP1: After message acceptance and deduplication (message is persisted)
   - RP2: After context loading (context is assembled)
   - RP3: After AI response generation (response is available)
   - RP4: After state persistence (conversation state is updated)
   - RP5: After response delivery (father has received the message)

2. WHEN a failure occurs between RP1 and RP3 (context loading or AI generation fails), THE Conversation_Engine SHALL:
   - Deliver a Fallback_Response to the father
   - Persist the father's message in conversation history
   - Schedule a deferred regeneration attempt
   - The conversation remains ACTIVE

3. WHEN a failure occurs between RP3 and RP4 (state persistence fails), THE Conversation_Engine SHALL:
   - Retry persistence with the same data (idempotent write)
   - If retry succeeds: deliver the response normally
   - If retry fails: deliver the response but log a consistency alert (response delivered, state not persisted — next pipeline execution will reconcile)

4. WHEN a failure occurs between RP4 and RP5 (delivery fails), THE Conversation_Engine SHALL:
   - Queue the message for redelivery
   - The conversation state is already persisted and consistent
   - Redelivery does not re-execute the pipeline

5. WHEN a deferred regeneration attempt succeeds (scheduled after a fallback was delivered), THE Conversation_Engine SHALL:
   - NOT deliver a second response (the father already received the fallback)
   - Store the regenerated response for use if the father sends another message (the context will be richer)
   - Log the successful regeneration for quality tracking

6. THE Conversation_Engine SHALL detect stale conversations on startup or recovery: if a conversation is in ACTIVE state but last_message_at is older than the conversation's expiration window, transition it to EXPIRED

7. WHEN the system restarts and pending side-effects exist (queued memory extraction, queued events), THE Conversation_Engine SHALL resume processing them without duplication (idempotent side-effect execution)

8. THE Conversation_Engine does NOT require compensation (rollback of previously succeeded external actions) for partial failures. Justification:
   - The synchronous phase is atomic: state persistence either fully succeeds or fully fails (Requirement 9 criteria 7). If persistence fails, no external state has changed.
   - Response delivery (RP4 → RP5) is the only external action after persistence. If delivery fails, the response is queued for redelivery — no state rollback needed because the persisted state is correct.
   - Asynchronous side-effects are all idempotent and best-effort (except event publication which retries until delivered). A failed side-effect does not corrupt conversation state.
   - Therefore: recovery (retry/reconcile) is sufficient. No compensating transactions are required by product behavior.


---

### Requirement 9: Concurrency

**User Story:** As a product owner, I want the system to handle concurrent messages safely, so that no race condition can corrupt conversation state or produce duplicate responses.

#### Acceptance Criteria

1. THE Conversation_Engine SHALL acquire a Session_Lock per father before executing the orchestration pipeline. At most one pipeline execution SHALL be in progress for any given father at any time.

2. WHEN a second Inbound_Message arrives for a father while the Session_Lock is held, THE Conversation_Engine SHALL queue the message and process it after the current pipeline execution completes and releases the lock

3. THE Session_Lock SHALL have a maximum hold duration (configurable, default: 45 seconds). If the lock is not released within this duration, it SHALL be forcibly released and the pipeline execution treated as failed (triggering recovery per Requirement 8).

4. THE Conversation_Engine SHALL process queued messages in receipt order after lock release. If multiple messages queued during a single lock hold, they are processed sequentially (one pipeline per message).

5. THE Conversation_Engine SHALL prevent duplicate response delivery: if the same Inbound_Message triggers the pipeline twice (due to retry), the Idempotency_Key ensures only one response is delivered

6. WHEN side-effects are scheduled (memory extraction, event publication), they SHALL execute outside the Session_Lock scope — side-effects do not hold the lock and can execute concurrently with a subsequent pipeline execution for the same father

7. THE Conversation_Engine SHALL ensure that conversation state transitions are atomic: either the entire state change (message persisted + counter updated + state transitioned) succeeds, or none of it persists

---

### Requirement 10: Business Timing

**User Story:** As a product owner, I want precise timing rules for conversation lifecycle, so that conversations expire predictably and follow-ups happen at the right moments.

#### Acceptance Criteria

1. THE Conversation_Engine SHALL enforce conversation expiration windows per type:

   | Conversation Type | Expiration Window | Idle Timeout |
   |------------------|-------------------|--------------|
   | ONBOARDING | 48 hours from creation | 48 hours without father message |
   | DAILY_COACHING | 24 hours from creation | 24 hours without father message |
   | FOLLOW_UP | 24 hours from creation | 24 hours without father message |
   | REFLECTION | 24 hours from creation | 24 hours without father message |
   | INACTIVITY_CHECK | 48 hours from creation | 48 hours without father message |
   | CELEBRATION | 24 hours from creation | 24 hours without father message |
   | DIFFICULT_SITUATION | No expiration | 48 hours without father message (→ ABANDONED) |

2. WHEN a conversation's idle timeout is reached (no father message within the specified window), THE Conversation_Engine SHALL transition it to EXPIRED (or ABANDONED for DIFFICULT_SITUATION)

3. THE Conversation_Engine SHALL evaluate expiration conditions:
   - On every inbound message (check if the active conversation has expired before processing)
   - On a periodic schedule (configurable, default: every 15 minutes) to catch conversations that expire without inbound activity

4. WHEN a conversation reaches COMPLETED or EXPIRED state, THE Conversation_Engine SHALL enforce a cooldown period before initiating any new proactive conversation:
   - After COMPLETED: no cooldown (new proactive allowed at next scheduled time)
   - After EXPIRED: 24-hour cooldown (per SPEC-002 Requirement 12 criteria 2)
   - After ABANDONED: 48-hour cooldown
   Father-initiated messages always override cooldowns — a new conversation is created immediately.

5. THE Conversation_Engine SHALL evaluate whether a new inbound message belongs to the existing ACTIVE conversation or should start a new one:
   - If an ACTIVE conversation exists and has not expired: route to existing conversation
   - If an ACTIVE conversation exists but has expired: transition to EXPIRED, then create a new conversation for the inbound message
   - If no ACTIVE conversation exists: create a new conversation

6. WHEN a father responds to a coaching message after the conversation has expired but within 4 hours of expiration, THE Conversation_Engine SHALL create a new conversation of the same type (treating the response as continuation intent)

7. THE Conversation_Engine SHALL respect Quiet_Hours (21:00-07:00 father's local time per SPEC-002 Requirement 10 criteria 1): proactive conversations are never initiated during this window. Father-initiated messages during Quiet_Hours are processed normally.

---

### Requirement 11: Event Publication

**User Story:** As a product owner, I want the orchestration layer to publish well-defined business events, so that other subsystems can react to conversation outcomes without tight coupling.

#### Acceptance Criteria

1. THE Conversation_Engine SHALL publish the following Business_Events:

   | Event | Trigger | Key Data |
   |-------|---------|----------|
   | CONVERSATION_STARTED | New conversation created | father_id, conversation_id, conversation_type |
   | CONVERSATION_COMPLETED | Conversation reaches COMPLETED | father_id, conversation_id, type, message_count, duration |
   | CONVERSATION_EXPIRED | Conversation reaches EXPIRED | father_id, conversation_id, type, last_message_at |
   | CONVERSATION_ABANDONED | Conversation reaches ABANDONED | father_id, conversation_id, type |
   | MESSAGE_RECEIVED | Inbound message accepted | father_id, conversation_id, message_id |
   | MESSAGE_SENT | Outbound message delivered | father_id, conversation_id, message_id |
   | MISSION_COMPLETED | Mission transitions to COMPLETED | father_id, mission_id, child_id, outcome_rating |
   | MISSION_ASSIGNED | New mission created | father_id, mission_id, child_id, category, difficulty |
   | MISSION_EXPIRED | Mission deadline passed | father_id, mission_id, child_id |
   | SAFETY_ESCALATION | Non-SAFE message classification | father_id, classification, confidence |
   | AI_FALLBACK_USED | Fallback response delivered | father_id, conversation_id, failure_reason |
   | FATHER_REGISTERED | New father record created | father_id |

2. THE Conversation_Engine SHALL publish events ONLY after the triggering state change has been successfully persisted — never before persistence (to prevent phantom events). If state persistence fails, no event is published for that transition.

3. THE Conversation_Engine SHALL publish events as asynchronous side-effects outside the Session_Lock scope. The Conversation_Engine is responsible for event content and publication timing. Event delivery mechanics (transport, durability, consumer registration) are outside this specification's scope and belong to the infrastructure layer.

4. THE Conversation_Engine SHALL assign a monotonically increasing sequence number per father to all published events, enabling consumers to detect gaps or ordering issues. The sequence number is generated and assigned within the synchronous phase (before lock release) to guarantee monotonicity.

5. THE Conversation_Engine SHALL guarantee at-least-once delivery for all Business_Events. Consumers must handle duplicate events idempotently.

6. THE Conversation_Engine SHALL include a correlation_id (the Inbound_Message Idempotency_Key or the scheduled trigger identifier) in every published event, enabling end-to-end tracing

---

### Requirement 12: Operational Rules

**User Story:** As a product owner, I want clear boundaries between synchronous and asynchronous work, so that the system remains responsive and failures in background processing never impact the father's experience.

#### Acceptance Criteria

1. THE Conversation_Engine SHALL divide work into two phases:
   - **Synchronous phase** (within Session_Lock, within 30-second budget): message validation, context loading, AI generation, response validation, state persistence, response delivery
   - **Asynchronous phase** (after lock release, no time constraint): side-effects scheduled during the synchronous phase

   The Conversation_Engine SHALL support the following asynchronous side-effects:

   | Side-Effect | Trigger | Mandatory? | Ordering | Failure Behavior |
   |-------------|---------|-----------|----------|-----------------|
   | Memory extraction | Conversation COMPLETED or EXPIRED (2+ messages) | Best-effort | No ordering dependency | Queue for retry per SPEC-004 Req 20 criteria 8; log failure; no rollback |
   | Memory access tracking | Memories Injected or Referenced | Best-effort | No ordering dependency | Log failure; skip (metadata update is non-critical) |
   | Conversation Summary request | Conversation COMPLETED | Best-effort | After memory extraction (same batch) | Queue for retry; no rollback |
   | Event publication | State transition persisted | Mandatory (at-least-once) | Sequenced per father (monotonic sequence number) | Retry indefinitely until delivered; no rollback |
   | Metric updates | Various (mission completed, streak change) | Best-effort | No ordering dependency | Log failure; recalculate on next daily job |
   | Deferred AI regeneration | Fallback_Response delivered | Best-effort | N/A | Single retry after configured delay; if fails, discard |
   | Scheduled trigger queueing | Follow-up action recommended | Best-effort | N/A | Log failure; trigger will re-evaluate at next scheduled time |

   **Mandatory** side-effects must eventually succeed (retry until delivered). **Best-effort** side-effects are retried a bounded number of times, then abandoned with logging.

2. THE Conversation_Engine SHALL ensure that failure in any asynchronous side-effect NEVER impacts the father's experience. If a side-effect fails:
   - The father has already received their response
   - The side-effect is queued for retry
   - No conversation state is rolled back

3. THE Conversation_Engine SHALL ensure all synchronous operations are deterministic given the same input: the same father message + the same loaded context SHALL always produce the same orchestration decisions (action selection, validation results, state transitions). Only the AI response content itself is non-deterministic.

4. THE Conversation_Engine SHALL isolate failures: a failure processing one father's message SHALL NOT impact processing for any other father. Per-father isolation is enforced through independent Session_Locks and independent pipeline executions.

5. THE Conversation_Engine SHALL enforce the daily AI call limit per father (20 calls per day per SPEC-002 Requirement 10 criteria 12). When the limit is reached, THE Conversation_Engine SHALL use cached or Fallback_Responses for remaining interactions that day.

6. THE Conversation_Engine SHALL enforce the daily outbound notification limit (5 proactive messages per day per SPEC-002 Requirement 10 criteria 2). Replies within active conversations do not count toward this limit.

7. THE Conversation_Engine SHALL track per-father daily counters: ai_calls_today, proactive_messages_today, last_outbound_at. Counters reset at midnight in the father's local timezone.


---

### Requirement 13: Edge Cases

**User Story:** As a product owner, I want all orchestration edge cases handled gracefully, so that the system remains robust regardless of timing, ordering, or failure scenarios.

#### Acceptance Criteria

1. WHEN the AI is unavailable for an extended period (> 5 minutes), THE Conversation_Engine SHALL:
   - Deliver Fallback_Responses to all incoming messages
   - NOT transition conversations to terminal states (they remain ACTIVE)
   - Queue deferred AI generation for each missed response
   - Publish AI_FALLBACK_USED events for operational monitoring

2. WHEN duplicate messages are detected (same Idempotency_Key), THE Conversation_Engine SHALL return the cached response from the first successful processing without executing the pipeline again. If the first processing is still in progress, the duplicate waits for completion.

3. WHEN the AI returns an empty or null response, THE Conversation_Engine SHALL treat it as a validation failure and follow the retry → fallback chain (Requirement 5 criteria 2)

4. WHEN state persistence partially fails (e.g., message persisted but counter not updated), THE Conversation_Engine SHALL:
   - Detect the inconsistency on the next pipeline execution for that father
   - Reconcile by recomputing derived state from persisted messages
   - Log the reconciliation for operational awareness

5. WHEN the AI returns invalid structured output (wrong schema, missing fields, out-of-range values), THE Conversation_Engine SHALL:
   - Log the invalid output with full prompt context
   - Retry once with a correction instruction
   - If retry also invalid: use Fallback_Response and log for AI quality review

6. WHEN a father sends a message that would reopen a recently COMPLETED conversation (within 4 hours), THE Conversation_Engine SHALL create a new conversation of the appropriate type rather than reopening the completed one. Completed conversations are immutable.

7. WHEN a mission has already been completed and the father sends another message about it, THE Conversation_Engine SHALL acknowledge the completed status and respond conversationally without attempting a second state transition

8. WHEN memory extraction fails for a conversation, THE Conversation_Engine SHALL:
   - Queue the extraction for retry (per SPEC-004 Requirement 20 criteria 8)
   - NOT impact the father's next conversation (extraction failure is invisible to the father)
   - The conversation transcript remains available for manual or deferred extraction

9. WHEN the father sends a message during Quiet_Hours that triggers a proactive follow-up (e.g., mission completion → celebration), THE Conversation_Engine SHALL process the inbound message immediately but queue any proactive follow-up messages until Quiet_Hours end (07:00 father's local time)

10. WHEN a scheduled conversation trigger fires (daily coaching time) but the father already has an ACTIVE conversation, THE Conversation_Engine SHALL queue the trigger and evaluate it after the current conversation reaches a terminal state

11. WHEN the father's status is PAUSED, THE Conversation_Engine SHALL:
    - Accept inbound messages (if the father initiates, process normally and resume)
    - NOT initiate any proactive conversations
    - NOT deliver any scheduled messages

12. WHEN the father's status is CHURNED and a message arrives, THE Conversation_Engine SHALL:
    - Transition the father to REACTIVATED status (per SPEC-002 Requirement 1 criteria 5)
    - Trigger the memory reactivation process (per SPEC-004 Requirement 21 criteria 4)
    - Create a reactivation conversation
    - Process the message within that conversation

---

### Requirement 14: Cross-Spec Compatibility

**User Story:** As an architect, I want explicit verification that the orchestration layer is compatible with all other specifications, so that no contradictions exist across the system.

#### Acceptance Criteria

1. THE Conversation_Engine SHALL respect all Father status constraints defined in SPEC-002 Requirement 11 criteria 1. Conversations are only created for fathers in status: NOT_STARTED (triggers onboarding), ONBOARDING, ACTIVE, REACTIVATED. Conversations are never created for PAUSED (unless father-initiated), CHURNED (unless father-initiated, which triggers reactivation), or DELETED fathers.

2. THE Conversation_Engine SHALL respect the conversation types and their triggers as defined in SPEC-002 Requirement 8 criteria 1. No new conversation types are introduced by this specification.

3. THE Conversation_Engine SHALL delegate all AI prompt assembly, model selection, and output schema validation to the Intelligence_Layer as defined in SPEC-003. The orchestration layer owns the decision of WHEN to call the AI and WHAT to do with the result; SPEC-003 owns HOW the AI processes the request.

4. THE Conversation_Engine SHALL delegate all memory retrieval, storage, and lifecycle management to the Memory_System as defined in SPEC-004. The orchestration layer triggers memory operations at the appropriate moments; SPEC-004 owns the memory lifecycle rules.

5. THE Conversation_Engine SHALL enforce all business constraints from SPEC-002 Requirement 10 (Quiet_Hours, daily limits, message limits, response latency). These constraints are applied within the orchestration pipeline, not delegated to other subsystems.

6. THE Conversation_Engine SHALL respect the AI decision boundary from SPEC-003 Requirement 14: the AI produces recommendations, the orchestration layer validates and executes them. The orchestration layer has final authority over all state transitions.

7. THE Conversation_Engine SHALL use the AI output contracts defined in SPEC-003 Requirement 15 as the interface between AI orchestration and post-processing. No additional output formats are introduced.

8. THE Conversation_Engine SHALL use the memory retrieval metadata contract defined in SPEC-004 Requirement 19 as the interface for memory context loading. No additional memory query formats are introduced.

9. THE Conversation_Engine SHALL publish events that enable other subsystems to function without polling: the Coaching_Engine uses CONVERSATION_COMPLETED to evaluate phase progression, the Notification_System uses MISSION_ASSIGNED to track delivery, and the Memory_System uses conversation completion events to trigger extraction.

10. THE Conversation_Engine SHALL not duplicate or redefine any business rule from SPEC-002. Where this specification references a business rule, it cites the authoritative source in SPEC-002.

11. THE Conversation_Engine SHALL not implement the internal logic of any subsystem it coordinates. Specifically:
    - It SHALL NOT implement prompt assembly logic (owned by SPEC-003)
    - It SHALL NOT implement memory scoring, extraction, or consolidation logic (owned by SPEC-004)
    - It SHALL NOT implement mission generation or difficulty calculation logic (owned by SPEC-003 Requirement 7)
    - It SHALL NOT implement engagement score calculation (owned by SPEC-002 Requirement 9)
    It invokes these subsystems through their defined interfaces and validates their outputs before applying state transitions.
