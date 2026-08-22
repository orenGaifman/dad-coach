# Chatbot Conversation Bugs - Bugfix Design

## Overview

This design document addresses six interrelated bugs identified from WhatsApp chatbot conversation logs in the Dad Coach application. These bugs affect:

1. **Duplicate Message Processing** - Messages processed multiple times due to webhook retries
2. **Generic Error Responses** - Unhelpful "משהו השתבש" errors without context
3. **Date/Time Calculation Error** - "Tomorrow" incorrectly showing today's date
4. **QUALITY_TIME_FOLLOW_UP State Bug** - Follow-up asking about future instead of completed QT
5. **Missing Frustration Handler** - No empathetic response to user frustration
6. **Status Dictionary for Dashboard** - WAITING state too vague for debugging

The fix strategy focuses on targeted, minimal changes that address root causes while preserving all existing functionality for non-buggy inputs.

## Glossary

- **Bug_Condition (C)**: The specific input/state combination that triggers each bug
- **Property (P)**: The desired behavior when the bug condition is met
- **Preservation**: Existing behaviors that must remain unchanged by the fix
- **Idempotency Key**: WhatsApp message ID used to detect duplicate webhook deliveries
- **Content Fingerprint**: Hash of message content + sender + timestamp for secondary duplicate detection
- **Fallback Template**: Pre-written response used when AI generation fails
- **WorkflowState**: Enum defining the father's current state in the conversation flow
- **SystemState**: Read-only snapshot of all data needed for workflow decisions
- **PatternMatcher**: Component that matches user messages to predefined regex patterns
- **StateHandler**: Component responsible for processing messages in a specific workflow state

## Bug Details

### Bug 1: Duplicate Message Processing

The bug manifests when WhatsApp sends the same message multiple times due to webhook retries. The `WorkflowIdempotencyService` only checks by WhatsApp message ID (`idempotencyKey`), but duplicate messages with different webhook delivery IDs bypass detection.

**Formal Specification:**
```
FUNCTION isDuplicateMessageBug(msg)
  INPUT: msg of type InboundMessage
  OUTPUT: boolean
  
  existingMsg ← findRecentMessage(msg.sender, msg.content, 60_SECONDS)
  RETURN existingMsg IS NOT NULL
         AND msg.idempotencyKey NOT IN processedKeys
         AND contentFingerprint(msg) = contentFingerprint(existingMsg)
END FUNCTION
```

**Examples:**
- Message "כן" received at 18:01:55, same "כן" received at 18:02:18 → Both processed (BUG)
- WhatsApp retries same message with different delivery ID → Processed again (BUG)
- Two different messages "כן" and "לא" from same user → Both processed correctly (OK)

### Bug 2: Generic Error Responses

When an error occurs in `WorkflowEngineImpl.doProcessMessage()`, the system catches exceptions and returns "Something went wrong. Please try again." without attempting state-specific fallbacks or logging sufficient context.

**Formal Specification:**
```
FUNCTION isGenericErrorBug(error, state, response)
  INPUT: error of type Exception, state of type WorkflowState, response of type String
  OUTPUT: boolean
  
  RETURN error IS NOT NULL
         AND response CONTAINS "went wrong" OR response CONTAINS "השתבש"
         AND stateSpecificFallbackExists(state)
         AND NOT logContainsFullContext(error)
END FUNCTION
```

**Examples:**
- AI timeout in SCHEDULE_QUALITY_TIME → Returns generic error instead of "Having trouble finding slots, please try again" (BUG)
- Database error in FOLLOW_UP → Returns generic error without logging father_id, state, message (BUG)

### Bug 3: Date/Time Calculation Error

Date formatting for "tomorrow" incorrectly returns today's date in some cases. The issue arises when date calculations don't consistently use the father's configured timezone.

**Formal Specification:**
```
FUNCTION isDateCalculationBug(displayedDate, messageContent, fatherTimezone)
  INPUT: displayedDate of type LocalDate, messageContent of type String, fatherTimezone of type ZoneId
  OUTPUT: boolean
  
  todayInFatherTz ← LocalDate.now(fatherTimezone)
  tomorrowInFatherTz ← todayInFatherTz.plusDays(1)
  
  RETURN messageContent.contains("מחר") OR messageContent.contains("tomorrow")
         AND extractDate(displayedDate) = todayInFatherTz
         AND extractDate(displayedDate) ≠ tomorrowInFatherTz
END FUNCTION
```

**Examples:**
- "מחר, יום שישי 22/08" shown when today IS Friday 22/08 (BUG)
- Server in UTC, father in Asia/Jerusalem, date crosses midnight differently (BUG)

### Bug 4: QUALITY_TIME_FOLLOW_UP State Bug

The `FollowUpStateHandler.findQualityTimeForFollowUp()` looks for Quality Time with `SCHEDULED` status, but for follow-ups, it should find Quality Time where `end_time < now` (completed ones needing follow-up).

**Formal Specification:**
```
FUNCTION isFollowUpStateBug(father, response, qualityTimeEvents)
  INPUT: father of type Father, response of type String, qualityTimeEvents of type List<QualityTimeEvent>
  OUTPUT: boolean
  
  completedQT ← qualityTimeEvents.filter(qt → qt.end_time < NOW)
  upcomingQT ← qualityTimeEvents.filter(qt → qt.start_time > NOW)
  
  RETURN father.currentState = QUALITY_TIME_FOLLOW_UP
         AND (response.referencesQT(upcomingQT) OR NOT response.asksAboutCompletedQT(completedQT))
END FUNCTION
```

**Examples:**
- QT ended at 15:00, father is in FOLLOW_UP state → Bot asks about NEXT scheduled QT instead of completed one (BUG)
- Response: "Ready for your Quality Time tomorrow?" in FOLLOW_UP state (BUG)

### Bug 5: Missing Frustration Handler

When users express frustration ("why do I need to repeat myself", "כבר אמרתי"), the bot ignores the emotional content and continues with standard flow.

**Formal Specification:**
```
FUNCTION isFrustrationHandlerBug(message, response)
  INPUT: message of type String, response of type String
  OUTPUT: boolean
  
  frustrationPatterns ← ["why again", "repeat", "already said", "למה שוב", "כבר אמרתי", "שוב", "אתה שואל"]
  empathyPhrases ← ["sorry", "understand", "apologize", "מצטער", "מבין", "סליחה"]
  
  RETURN containsAny(message, frustrationPatterns)
         AND NOT containsAny(response, empathyPhrases)
END FUNCTION
```

**Examples:**
- "למה אתה שואל שוב?" → Bot responds with standard clarification without empathy (BUG)
- "כבר אמרתי לך שכן" → Bot ignores frustration indicator (BUG)

### Bug 6: Status Dictionary for Dashboard

The dashboard shows only the state name (e.g., "WAITING") without context about what specific phase the father is in.

**Formal Specification:**
```
FUNCTION isVagueStatusBug(dashboardDisplay, fatherState)
  INPUT: dashboardDisplay of type StatusDisplay, fatherState of type FatherState
  OUTPUT: boolean
  
  RETURN dashboardDisplay.showsOnlyStateName()
         AND NOT dashboardDisplay.hasContextDescription()
         AND NOT dashboardDisplay.hasStatusDictionary()
END FUNCTION
```

**Examples:**
- Dashboard shows "WAITING" → Should show "Waiting for QT tomorrow at 3pm" (BUG)
- No status dictionary table explaining what each state means (BUG)

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**

1. **Non-duplicate messages** must continue to be processed normally
2. **Successful message processing** must return AI/template responses without modification
3. **Security errors** must return generic messages without exposing internals
4. **Correct timezone handling** for fathers with properly configured timezones must remain unchanged
5. **Standard workflow transitions** must continue using existing WorkflowState enum
6. **Pattern matching** for non-frustration messages must work as before
7. **Dashboard basic functionality** must remain operational while adding new features

**Scope:**
All inputs that do NOT trigger the bug conditions should be completely unaffected by these fixes.

## Hypothesized Root Cause

### Bug 1: Duplicate Message Processing
1. **Single-key idempotency**: `WorkflowIdempotencyService` only checks WhatsApp message ID
2. **Missing content fingerprinting**: No secondary check based on content + sender + timestamp
3. **Timing window**: Messages arriving within 60 seconds with same content not detected

### Bug 2: Generic Error Responses
1. **Catch-all handler**: `doProcessMessage()` catches all exceptions with single generic response
2. **Missing fallback chain**: No attempt to use state-specific templates before generic error
3. **Insufficient logging**: Error context (father_id, state, message) not consistently logged

### Bug 3: Date/Time Calculation Error
1. **Server timezone usage**: Some calculations use `LocalDate.now()` without timezone
2. **Inconsistent timezone propagation**: Father's timezone not passed to all date formatting methods
3. **Missing validation**: No verification that day-of-week matches actual date

### Bug 4: QUALITY_TIME_FOLLOW_UP State Bug
1. **Wrong filter criteria**: `findQualityTimeForFollowUp()` filters by `status = "SCHEDULED"` 
2. **Should filter by**: `end_time < now` to find completed QT needing follow-up
3. **State loader issue**: `getNextScheduledQualityTime()` returns upcoming instead of completed

### Bug 5: Missing Frustration Handler
1. **No frustration patterns**: `StatePatterns` doesn't include frustration detection patterns
2. **No empathy action**: `WorkflowAction` enum lacks `ACKNOWLEDGE_FRUSTRATION` action
3. **Missing handler logic**: State handlers don't check for frustration before pattern matching

### Bug 6: Status Dictionary for Dashboard
1. **Missing component**: No `StatusDictionaryPanel` component in dashboard
2. **No context mapping**: No mapping from state → context description
3. **API doesn't provide context**: Backend doesn't return state context in father state response

## Correctness Properties

Property 1: Bug Condition - Duplicate Message Detection

_For any_ inbound message where identical content from the same sender exists within 60 seconds, the fixed `WorkflowIdempotencyService` SHALL detect the duplicate using content fingerprinting and return the cached response without reprocessing.

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation - Distinct Message Processing

_For any_ message that is NOT a duplicate (different content, different sender, or outside time window), the fixed code SHALL process it normally and produce the same result as the original system.

**Validates: Requirements 3.1, 3.2**

Property 3: Bug Condition - State-Specific Error Handling

_For any_ error occurring during message processing where a state-specific fallback exists, the fixed `WorkflowEngineImpl` SHALL attempt to use the state-specific fallback template and log comprehensive error context before falling back to generic error.

**Validates: Requirements 2.4, 2.5, 2.6**

Property 4: Preservation - Generic Error Security

_For any_ security-related error or error without state-specific fallback, the fixed code SHALL continue to return generic error messages without exposing internal details.

**Validates: Requirements 3.3, 3.4**

Property 5: Bug Condition - Timezone-Aware Date Calculation

_For any_ date calculation involving "tomorrow" or day-of-week display, the fixed code SHALL use the father's configured timezone from their profile to compute the correct date.

**Validates: Requirements 2.7, 2.8, 2.9**

Property 6: Preservation - Existing Timezone Handling

_For any_ date formatting where timezone is already correctly used, the fixed code SHALL produce identical output to the original system.

**Validates: Requirements 3.5, 3.6**

Property 7: Bug Condition - Follow-Up State Quality Time Selection

_For any_ father in QUALITY_TIME_FOLLOW_UP state, the fixed `FollowUpStateHandler` SHALL find and reference the Quality Time event where `end_time < now` (completed session) rather than upcoming scheduled events.

**Validates: Requirements 2.10, 2.11, 2.12**

Property 8: Preservation - Other State Handling

_For any_ father NOT in QUALITY_TIME_FOLLOW_UP state, the fixed code SHALL use the same Quality Time selection logic as the original system.

**Validates: Requirements 3.7, 3.8**

Property 9: Bug Condition - Frustration Pattern Detection

_For any_ user message containing frustration indicators, the fixed `PatternMatcher` SHALL detect the frustration and the response SHALL include an empathetic acknowledgment before continuing with workflow-appropriate action.

**Validates: Requirements 2.13, 2.14, 2.15**

Property 10: Preservation - Non-Frustration Message Handling

_For any_ message WITHOUT frustration indicators, the fixed code SHALL process it through standard pattern matching and produce the same result as the original system.

**Validates: Requirements 3.9, 3.10**

Property 11: Bug Condition - Status Dictionary Display

_For any_ father displayed in the dashboard, the fixed dashboard SHALL show a status dictionary panel with state definitions and contextual descriptions beyond just the state name.

**Validates: Requirements 2.16, 2.17, 2.18**

Property 12: Preservation - Existing Dashboard Functionality

_For any_ dashboard interaction, the fixed code SHALL maintain all existing functionality including father selection, auto-refresh, and state display while adding new status dictionary features.

**Validates: Requirements 3.11, 3.12**

## Fix Implementation

### Bug 1: Duplicate Message Processing

**File**: `backend/src/main/java/com/dadcoach/workflow/idempotency/WorkflowIdempotencyService.java`

**Changes Required:**

1. **Add content fingerprint cache**:
   ```java
   // New cache: contentFingerprint -> CachedResponse
   private final Map<String, CachedResponse> contentFingerprintCache = new ConcurrentHashMap<>();
   
   // Fingerprint TTL - 60 seconds for content-based detection
   private static final Duration FINGERPRINT_TTL = Duration.ofSeconds(60);
   ```

2. **Add fingerprint generation method**:
   ```java
   /**
    * Generates a content fingerprint from sender + content hash.
    */
   private String generateContentFingerprint(String sender, String content) {
       String normalized = (sender + "|" + content.trim().toLowerCase()).strip();
       return DigestUtils.sha256Hex(normalized);
   }
   ```

3. **Enhance checkDuplicate method**:
   ```java
   public Optional<OutboundMessageDto> checkDuplicate(String idempotencyKey, String sender, String content) {
       // Check primary key first (existing logic)
       if (idempotencyKey != null && !idempotencyKey.isBlank()) {
           CachedResponse cached = cache.get(idempotencyKey);
           if (cached != null && !cached.isExpired()) {
               log.info("Duplicate detected by idempotency key: {}", idempotencyKey);
               return Optional.of(cached.response());
           }
       }
       
       // Check content fingerprint (new logic)
       String fingerprint = generateContentFingerprint(sender, content);
       CachedResponse fingerprintCached = contentFingerprintCache.get(fingerprint);
       if (fingerprintCached != null && !fingerprintCached.isExpiredShort()) {
           log.info("Duplicate detected by content fingerprint for sender: {}", sender);
           return Optional.of(fingerprintCached.response());
       }
       
       return Optional.empty();
   }
   ```

4. **Update recordProcessed method**:
   ```java
   public void recordProcessed(String idempotencyKey, String sender, String content, OutboundMessageDto response) {
       // Record by idempotency key (existing logic)
       if (idempotencyKey != null && !idempotencyKey.isBlank()) {
           cache.put(idempotencyKey, new CachedResponse(response, Instant.now()));
       }
       
       // Record by content fingerprint (new logic)
       String fingerprint = generateContentFingerprint(sender, content);
       contentFingerprintCache.put(fingerprint, new CachedResponse(response, Instant.now()));
   }
   ```

**File**: `backend/src/main/java/com/dadcoach/workflow/WorkflowEngineImpl.java`

**Changes Required:**

1. **Update idempotency check call**:
   ```java
   // In doProcessMessage():
   Optional<OutboundMessageDto> cached = idempotencyService.checkDuplicate(
       idempotencyKey, 
       message.fatherChannelIdentity(), 
       message.textContent()
   );
   ```

2. **Update recordProcessed call**:
   ```java
   // At end of doProcessMessage():
   idempotencyService.recordProcessed(
       idempotencyKey,
       message.fatherChannelIdentity(),
       message.textContent(),
       response
   );
   ```

---

### Bug 2: Generic Error Responses

**File**: `backend/src/main/java/com/dadcoach/workflow/WorkflowEngineImpl.java`

**Changes Required:**

1. **Add state-specific error handling in catch block**:
   ```java
   } catch (Exception e) {
       // Enhanced error logging with full context
       log.error("Error processing message: father_id={}, state={}, message={}, error_type={}", 
           fatherId, currentState, truncateForLog(messageText), 
           e.getClass().getSimpleName(), e);
       
       // Attempt state-specific fallback before generic error
       String errorResponse = getStateSpecificErrorResponse(currentState, father.getLocale());
       return createErrorResponse(fatherUuid, father.getLocale(), errorResponse);
   }
   ```

2. **Add state-specific error method**:
   ```java
   /**
    * Returns a state-specific error message, falling back to generic if none exists.
    */
   private String getStateSpecificErrorResponse(WorkflowState state, String locale) {
       return switch (state) {
           case SCHEDULE_QUALITY_TIME -> locale.equals("he") 
               ? "מצטער, יש לי בעיה למצוא זמנים פנויים. אפשר לנסות שוב?"
               : "Sorry, I'm having trouble finding available slots. Can you try again?";
           case QUALITY_TIME_FOLLOW_UP -> locale.equals("he")
               ? "מצטער, משהו השתבש. ספר לי - האם השלמת את זמן האיכות?"
               : "Sorry, something went wrong. Tell me - did you complete your Quality Time?";
           case WAITING -> locale.equals("he")
               ? "מצטער, לא הצלחתי לעבד את ההודעה. מה תרצה לעשות?"
               : "Sorry, I couldn't process that. What would you like to do?";
           default -> locale.equals("he")
               ? "מצטער, משהו השתבש. אפשר לנסות שוב?"
               : "Sorry, something went wrong. Please try again.";
       };
   }
   ```

**File**: `backend/src/main/java/com/dadcoach/workflow/message/FallbackMessages.java`

**Changes Required:**

1. **Add state-specific error templates to MessageType enum and database**

---

### Bug 3: Date/Time Calculation Error

**File**: `backend/src/main/java/com/dadcoach/workflow/message/MessageContext.java`

**Changes Required:**

1. **Add timezone-aware "tomorrow" calculation method**:
   ```java
   /**
    * Calculates "tomorrow" in the father's timezone.
    * 
    * @return LocalDate representing tomorrow in the context's timezone
    */
   public LocalDate getTomorrowInTimezone() {
       ZoneId zone = getTimezoneAsZoneId();
       return LocalDate.now(zone).plusDays(1);
   }
   
   /**
    * Calculates "today" in the father's timezone.
    */
   public LocalDate getTodayInTimezone() {
       ZoneId zone = getTimezoneAsZoneId();
       return LocalDate.now(zone);
   }
   ```

2. **Add date validation method**:
   ```java
   /**
    * Validates that the day-of-week in a formatted string matches the actual date.
    */
   public boolean validateDayOfWeek(LocalDate date, String formattedString) {
       DayOfWeek actualDay = date.getDayOfWeek();
       // Check if formatted string contains correct day name for locale
       String expectedDayName = actualDay.getDisplayName(TextStyle.FULL, getDisplayLocale());
       return formattedString.contains(expectedDayName);
   }
   ```

**File**: `backend/src/main/java/com/dadcoach/workflow/message/FallbackMessages.java`

**Changes Required:**

1. **Update substitute method to use timezone-aware calculations**:
   ```java
   // When substituting {tomorrow}:
   if (template.contains("{tomorrow}")) {
       LocalDate tomorrow = context.getTomorrowInTimezone();
       String formattedTomorrow = formatDateForLocale(tomorrow, context.getLocale(), context.getTimezoneAsZoneId());
       result = result.replace("{tomorrow}", formattedTomorrow);
   }
   ```

---

### Bug 4: QUALITY_TIME_FOLLOW_UP State Bug

**File**: `backend/src/main/java/com/dadcoach/workflow/state/FollowUpStateHandler.java`

**Changes Required:**

1. **Fix findQualityTimeForFollowUp method**:
   ```java
   /**
    * Finds the Quality Time event that should be followed up on.
    * This looks for Quality Time events that have ENDED (end_time < now).
    *
    * @param state the system state
    * @return the quality time event to follow up on, or null if none found
    */
   private QualityTimeEvent findQualityTimeForFollowUp(SystemState state) {
       if (state.qualityTimeEvents() == null || state.qualityTimeEvents().isEmpty()) {
           return null;
       }
       
       Instant now = Instant.now();
       
       // Find the most recent Quality Time that has ENDED (end_time < now)
       // This is the one we're following up on
       return state.qualityTimeEvents().stream()
               .filter(qt -> qt.endTime() != null && qt.endTime().isBefore(now))
               .filter(qt -> "SCHEDULED".equals(qt.status())) // Still SCHEDULED means not yet processed
               .max(Comparator.comparing(QualityTimeEvent::endTime))
               .orElse(null);
   }
   ```

2. **Update handleMarkCompleted to use correct QT**:
   ```java
   private StateAction handleMarkCompleted(WorkflowContext context) {
       SystemState state = systemStateLoader.loadState(context.getFatherId());
       
       // Get the Quality Time that ENDED and needs follow-up (not upcoming)
       QualityTimeEvent qualityTimeEvent = findQualityTimeForFollowUp(state);
       
       // Fallback removed - don't use getNextScheduledQualityTime()
       // as that returns UPCOMING events, not completed ones
       
       if (qualityTimeEvent == null) {
           log.warn("No completed Quality Time found for follow-up, father: {}", context.getFatherId());
           return transitionToScheduleWithGenericMessage(state);
       }
       // ... rest of method unchanged
   }
   ```

---

### Bug 5: Frustration Handler

**File**: `backend/src/main/java/com/dadcoach/workflow/pattern/StatePatterns.java`

**Changes Required:**

1. **Add frustration patterns to each state pattern list**:
   ```java
   /**
    * Global frustration patterns that can match in any state.
    * Should be checked FIRST before state-specific patterns.
    */
   public static final List<StatePattern> FRUSTRATION_PATTERNS = List.of(
       // FRUSTRATION (English)
       StatePattern.of(
           "FRUSTRATION_EN",
           Pattern.compile("(?i).*(why again|repeat|already said|already told|you asked|asked before).*"),
           WorkflowAction.ACKNOWLEDGE_FRUSTRATION
       ),
       
       // FRUSTRATION (Hebrew)
       StatePattern.of(
           "FRUSTRATION_HE",
           Pattern.compile(".*(למה שוב|כבר אמרתי|שאלת כבר|אתה שואל שוב|חזור על).*"),
           WorkflowAction.ACKNOWLEDGE_FRUSTRATION
       )
   );
   ```

**File**: `backend/src/main/java/com/dadcoach/workflow/pattern/WorkflowAction.java`

**Changes Required:**

1. **Add ACKNOWLEDGE_FRUSTRATION action**:
   ```java
   /**
    * Acknowledges user frustration with empathetic response before continuing.
    */
   ACKNOWLEDGE_FRUSTRATION
   ```

**File**: `backend/src/main/java/com/dadcoach/workflow/WorkflowEngineImpl.java`

**Changes Required:**

1. **Add frustration check before state-specific pattern matching**:
   ```java
   // Check for frustration patterns FIRST (global patterns)
   Optional<PatternResult> frustrationMatch = patternMatcher.match(
       messageText, StatePatterns.FRUSTRATION_PATTERNS);
   
   if (frustrationMatch.isPresent() && frustrationMatch.get().isMatched()) {
       // Handle frustration with empathetic response + continue with normal flow
       String empathyPrefix = getEmpathyMessage(father.getLocale());
       // Continue to get normal response from state handler
       // Prepend empathy to final response
   }
   ```

2. **Add empathy message method**:
   ```java
   private String getEmpathyMessage(String locale) {
       return "he".equals(locale)
           ? "מצטער אם זה מרגיש חוזר על עצמו - אני כאן לעזור. "
           : "Sorry if this feels repetitive - I'm here to help. ";
   }
   ```

**File**: `backend/src/main/java/com/dadcoach/workflow/message/FallbackMessages.java`

**Changes Required:**

1. **Add FRUSTRATION_ACKNOWLEDGMENT message type and templates**

---

### Bug 6: Status Dictionary for Dashboard

**File**: `dad-coach-web/app/dev/dashboard/components/StatusDictionaryPanel.tsx` (NEW FILE)

**Changes Required:**

1. **Create new StatusDictionaryPanel component**:
   ```typescript
   'use client';
   
   /**
    * StatusDictionaryPanel - displays a reference table of all workflow states
    * with their definitions and descriptions.
    */
   
   interface StatusDefinition {
     state: string;
     displayName: string;
     description: string;
     type: 'state' | 'action';
     possibleActions: string[];
   }
   
   const STATUS_DEFINITIONS: StatusDefinition[] = [
     {
       state: 'WELCOME',
       displayName: 'Welcome',
       description: 'Initial state for new fathers. Explains Dad Coach and guides to first Quality Time.',
       type: 'state',
       possibleActions: ['TRANSITION_TO_SCHEDULE', 'EXPLAIN_AND_REPROMPT']
     },
     {
       state: 'SCHEDULE_QUALITY_TIME',
       displayName: 'Schedule Quality Time',
       description: 'Active scheduling. Reads calendar, suggests time slots, creates events.',
       type: 'state',
       possibleActions: ['SELECT_SLOT', 'POSTPONE_SCHEDULING', 'SHOW_MORE_SLOTS', 'PARSE_TIME']
     },
     {
       state: 'WAITING',
       displayName: 'Waiting',
       description: 'Passive waiting for scheduled Quality Time. Sends morning reminders.',
       type: 'state',
       possibleActions: ['SHOW_SCHEDULE', 'RESCHEDULE', 'TRANSITION_TO_ACTIVITY_IDEAS', 'SHOW_DASHBOARD_SUMMARY']
     },
     {
       state: 'QUALITY_TIME_FOLLOW_UP',
       displayName: 'Quality Time Follow-Up',
       description: 'Post-event state. Asks if father completed Quality Time, updates metrics.',
       type: 'state',
       possibleActions: ['MARK_COMPLETED', 'MARK_MISSED']
     },
     {
       state: 'ACTIVITY_IDEAS',
       displayName: 'Activity Ideas',
       description: 'On-demand state for activity suggestions. Returns to previous state when done.',
       type: 'state',
       possibleActions: ['SHOW_IDEA_DETAILS', 'GENERATE_MORE_IDEAS', 'RETURN_TO_PREVIOUS']
     }
   ];
   
   export function StatusDictionaryPanel() {
     return (
       <div className="bg-white/5 border border-white/10 rounded-xl p-4">
         <h3 className="text-white font-semibold mb-4 flex items-center gap-2">
           <span>📖</span> Status Dictionary
         </h3>
         <div className="overflow-x-auto">
           <table className="w-full text-sm">
             <thead>
               <tr className="text-gray-400 border-b border-white/10">
                 <th className="text-left py-2 px-2">State</th>
                 <th className="text-left py-2 px-2">Description</th>
                 <th className="text-left py-2 px-2">Actions</th>
               </tr>
             </thead>
             <tbody>
               {STATUS_DEFINITIONS.map((status) => (
                 <tr key={status.state} className="border-b border-white/5">
                   <td className="py-2 px-2">
                     <span className="text-xs px-2 py-1 rounded-full bg-purple-500/20 text-purple-300">
                       {status.displayName}
                     </span>
                   </td>
                   <td className="py-2 px-2 text-gray-300">{status.description}</td>
                   <td className="py-2 px-2">
                     <div className="flex flex-wrap gap-1">
                       {status.possibleActions.slice(0, 2).map((action) => (
                         <span key={action} className="text-xs text-gray-500">
                           {action}
                         </span>
                       ))}
                       {status.possibleActions.length > 2 && (
                         <span className="text-xs text-gray-600">
                           +{status.possibleActions.length - 2} more
                         </span>
                       )}
                     </div>
                   </td>
                 </tr>
               ))}
             </tbody>
           </table>
         </div>
       </div>
     );
   }
   ```

**File**: `dad-coach-web/app/dev/dashboard/page.tsx`

**Changes Required:**

1. **Import and add StatusDictionaryPanel**:
   ```typescript
   import { StatusDictionaryPanel } from './components/StatusDictionaryPanel';
   
   // In the return JSX, after QualityTimePanelPlaceholder:
   <StatusDictionaryPanel />
   ```

**File**: `dad-coach-web/app/dev/dashboard/components/FatherStatePanel.tsx`

**Changes Required:**

1. **Add contextual status description**:
   ```typescript
   function getStatusContext(state: string, nextQTTime?: string): string {
     switch (state) {
       case 'WAITING':
         return nextQTTime 
           ? `Waiting - QT scheduled for ${nextQTTime}`
           : 'Waiting - No QT scheduled';
       case 'QUALITY_TIME_FOLLOW_UP':
         return 'Following up on completed Quality Time';
       case 'SCHEDULE_QUALITY_TIME':
         return 'Selecting time slot for Quality Time';
       default:
         return state.replace(/_/g, ' ');
     }
   }
   ```

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach:
1. **Exploratory phase**: Surface counterexamples demonstrating bugs on unfixed code
2. **Verification phase**: Confirm fixes work correctly and preserve existing behavior

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate bugs BEFORE implementing fixes. Confirm or refute root cause analysis.

**Bug 1 - Duplicate Messages Test Plan:**
1. Send identical message twice within 60 seconds → Expect both processed (demonstrates bug)
2. Send with different webhook delivery IDs but same content → Expect both processed (demonstrates bug)

**Bug 2 - Generic Errors Test Plan:**
1. Inject AI timeout in SCHEDULE_QUALITY_TIME → Expect generic error (demonstrates bug)
2. Check logs for missing father_id context → Expect incomplete logs (demonstrates bug)

**Bug 3 - Date Calculation Test Plan:**
1. Set server to UTC, father timezone to Asia/Jerusalem
2. Request "tomorrow" near midnight boundary → Expect wrong date (demonstrates bug)

**Bug 4 - Follow-Up State Test Plan:**
1. Create QT that ended 1 hour ago, put father in FOLLOW_UP state
2. Trigger follow-up message → Expect reference to UPCOMING QT (demonstrates bug)

**Bug 5 - Frustration Handler Test Plan:**
1. Send "כבר אמרתי לך" in SCHEDULE_QUALITY_TIME → Expect no empathy (demonstrates bug)
2. Send "why are you asking again" → Expect generic response (demonstrates bug)

**Bug 6 - Dashboard Status Test Plan:**
1. View father in WAITING state → Expect only "WAITING" shown (demonstrates bug)
2. Check for status dictionary → Expect none exists (demonstrates bug)

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces expected behavior.

**Pseudocode:**
```
FOR ALL msg WHERE isDuplicateMessage(msg) DO
  result := processMessage_fixed(msg)
  ASSERT result = cachedResponse(msg) AND NOT reprocessed(msg)
END FOR

FOR ALL (error, state) WHERE hasProcessingError(error) AND fallbackExists(state) DO
  result := handleError_fixed(error, state)
  ASSERT result.hasStateSpecificMessage() AND logs.containsFullContext()
END FOR

FOR ALL (father, date) WHERE displayingTomorrow(father) DO
  result := formatDate_fixed(date, father.timezone)
  ASSERT result.date = LocalDate.now(father.timezone).plusDays(1)
END FOR

FOR ALL father WHERE father.state = QUALITY_TIME_FOLLOW_UP DO
  qt := findQualityTimeForFollowUp_fixed(father)
  ASSERT qt.endTime < now AND response.referencesCompletedQT(qt)
END FOR

FOR ALL msg WHERE containsFrustrationIndicator(msg) DO
  result := processMessage_fixed(msg)
  ASSERT result.containsEmpathy() AND result.continuesWorkflow()
END FOR

FOR ALL dashboardView WHERE displayingFatherStatus() DO
  result := renderDashboard_fixed()
  ASSERT result.hasStatusDictionary() AND result.hasContextualDescription()
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT F(input) = F_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

### Unit Tests

**Bug 1:**
- Test content fingerprint generation consistency
- Test duplicate detection with various timing scenarios
- Test cache expiration behavior

**Bug 2:**
- Test state-specific error message selection
- Test error logging contains required fields
- Test fallback chain progression

**Bug 3:**
- Test timezone-aware date calculation across various timezones
- Test day-of-week validation
- Test midnight boundary scenarios

**Bug 4:**
- Test findQualityTimeForFollowUp returns ended QT
- Test correct QT is referenced in follow-up message
- Test edge case: multiple ended QTs

**Bug 5:**
- Test frustration pattern detection in English
- Test frustration pattern detection in Hebrew
- Test empathy prefix is added to response

**Bug 6:**
- Test StatusDictionaryPanel renders all states
- Test contextual description generation
- Test dashboard integration

### Property-Based Tests

- Generate random message pairs and verify duplicate detection accuracy
- Generate random error scenarios and verify state-specific handling
- Generate random timezone configurations and verify date calculations
- Generate random QT schedules and verify correct QT selection for follow-up
- Generate random messages with/without frustration indicators and verify response appropriateness

### Integration Tests

- Full flow: duplicate message handling end-to-end
- Full flow: error handling with state recovery
- Full flow: date display across timezone boundaries
- Full flow: QUALITY_TIME_FOLLOW_UP state transition and message generation
- Full flow: frustration detection and empathetic response
- Dashboard: render with status dictionary and verify all components
