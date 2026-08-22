# Bugfix Requirements Document

## Introduction

This document addresses multiple conversation handling bugs identified from WhatsApp chatbot conversation logs in the Dad Coach application. The bugs affect message processing, error handling, date calculations, state management, and user experience. Fixing these issues will improve reliability, reduce user frustration, and enhance the overall chatbot experience.

**Bugs Covered:**
1. Duplicate Message Processing - Same WhatsApp message processed multiple times
2. Generic Error Responses - "משהו השתבש" without context recovery
3. Date/Time Calculation Error - "Tomorrow" shows today's date
4. QUALITY_TIME_FOLLOW_UP State Not Functioning - Follow-up asks about future instead of past QT
5. Missing User Frustration/Repetition Handler - No empathetic response to user frustration
6. "WAITING" State is Too Vague - Dashboard needs status dictionary table

## Bug Analysis

### Current Behavior (Defect)

**Bug 1: Duplicate Message Processing**

1.1 WHEN WhatsApp sends the same message multiple times due to webhook retries THEN the system processes each message as a new unique message, resulting in duplicate responses being sent to the user

1.2 WHEN a message is received with an identical text content and timestamp within a short time window (e.g., 18:01:55 and 18:02:18) THEN the system fails to recognize it as a duplicate and processes it independently

1.3 WHEN the idempotency check relies solely on the WhatsApp message ID THEN duplicate messages with different webhook delivery IDs but identical content bypass the duplicate detection

**Bug 2: Generic Error Responses ("משהו השתבש")**

1.4 WHEN an error occurs during message processing THEN the system returns a generic error message "משהו השתבש. אפשר לנסות שוב?" without logging sufficient error context for debugging

1.5 WHEN AI processing fails or times out THEN the system falls back to the generic error message without attempting to use fallback templates or provide context-specific guidance

1.6 WHEN an exception is caught in processMessage THEN the error details are logged but the conversation context (current state, last successful action) is lost, making recovery difficult

**Bug 3: Date/Time Calculation Error**

1.7 WHEN the bot calculates "tomorrow" for scheduling purposes THEN it incorrectly returns the current date in some cases (e.g., saying "מחר, יום שישי 22/08" when today IS Friday 22/08)

1.8 WHEN date calculations are performed THEN the system does not consistently use the father's configured timezone, leading to incorrect day-of-week and date displays

1.9 WHEN displaying dates in messages THEN the system may use server timezone instead of father's timezone, causing confusion when they differ

**Bug 4: QUALITY_TIME_FOLLOW_UP State Not Functioning**

1.10 WHEN the scheduler triggers "QT ended" and transitions the father to QUALITY_TIME_FOLLOW_UP state THEN the bot fails to ask "How did the quality time go?" and instead sends a greeting about future Quality Time

1.11 WHEN a father is in QUALITY_TIME_FOLLOW_UP state THEN the next message they receive references upcoming/scheduled Quality Time instead of the completed session they should be reflecting on

1.12 WHEN the FollowUpStateHandler generates a response THEN it may load the next scheduled Quality Time instead of the one that just ended, causing incorrect context

**Bug 5: Missing User Frustration/Repetition Handler**

1.13 WHEN a user expresses frustration such as "why do I need to repeat myself" or "I already told you" THEN the bot ignores the emotional content and responds with a generic clarification or continues with standard flow

1.14 WHEN context is lost due to errors or state issues THEN the system does not acknowledge the loss or apologize to the user, leading to increased frustration

1.15 WHEN a user repeats information they previously provided THEN the system does not recognize the repetition pattern or provide appropriate acknowledgment

**Bug 6: "WAITING" State is Too Vague**

1.16 WHEN a father is in WAITING state THEN various interaction types are all handled by the same state with different AI actions, making debugging and state tracking difficult

1.17 WHEN reviewing logs or dashboard data for fathers in WAITING state THEN it is unclear what specific phase of interaction they are in (waiting for QT to start, waiting for morning reminder, waiting for user input, etc.)

1.18 WHEN the WAITING state handles multiple scenarios THEN the AI action strings are the only way to distinguish between them, which is not reflected in the state machine visualization or status tracking

### Expected Behavior (Correct)

**Bug 1: Duplicate Message Processing**

2.1 WHEN a message is received with identical content from the same sender within a configurable time window (e.g., 60 seconds) THEN the system SHALL detect it as a duplicate and skip processing, returning the previously cached response

2.2 WHEN the idempotency service checks for duplicates THEN it SHALL consider both message ID AND a content+sender+timestamp fingerprint to catch all duplicate scenarios

2.3 WHEN a duplicate message is detected THEN the system SHALL log the duplicate detection event with appropriate context and return HTTP 200 to acknowledge receipt without reprocessing

**Bug 2: Generic Error Responses**

2.4 WHEN an error occurs during message processing THEN the system SHALL log comprehensive error context including: father_id, current_workflow_state, message_content, exception_type, and stack_trace

2.5 WHEN AI processing fails THEN the system SHALL attempt to use state-specific fallback templates before returning a generic error, providing the user with actionable guidance based on their current workflow state

2.6 WHEN an error occurs THEN the system SHALL preserve conversation context by logging the last known good state, offering state-specific recovery options, and including a reference ID in the error message for support debugging

**Bug 3: Date/Time Calculation Error**

2.7 WHEN the system calculates "tomorrow" THEN it SHALL correctly compute the next calendar day in the father's configured timezone, ensuring tomorrow is always day+1 from today

2.8 WHEN displaying any date or time in user-facing messages THEN the system SHALL always use the father's configured timezone from their profile (defaulting to "Asia/Jerusalem" if not set)

2.9 WHEN formatting dates for display THEN the system SHALL verify the day-of-week matches the actual calendar date in the father's timezone before sending

**Bug 4: QUALITY_TIME_FOLLOW_UP State**

2.10 WHEN the scheduler triggers QUALITY_TIME_ENDED and transitions to QUALITY_TIME_FOLLOW_UP THEN the system SHALL immediately send a follow-up question asking about the Quality Time that just concluded (e.g., "איך היה זמן האיכות עם {childName}?")

2.11 WHEN generating the follow-up message THEN the system SHALL reference the Quality Time event that has ended (status=SCHEDULED with end_time < now) rather than any upcoming scheduled events

2.12 WHEN the father responds in QUALITY_TIME_FOLLOW_UP state THEN the system SHALL process their response as feedback on the completed session (MARK_COMPLETED or MARK_MISSED) before transitioning to schedule new Quality Time

**Bug 5: User Frustration Handler**

2.13 WHEN a user message contains frustration indicators (e.g., "why again", "I said", "repeat", "למה שוב", "כבר אמרתי") THEN the system SHALL detect the frustration pattern and respond with an empathetic acknowledgment before continuing with the request

2.14 WHEN frustration is detected THEN the system SHALL respond with an apology message acknowledging that the user may have to repeat information, followed by a clear state-appropriate request

2.15 WHEN the system detects it may have lost context (e.g., after an error recovery) THEN it SHALL proactively acknowledge this possibility in its next response to the user

**Bug 6: Status Dictionary for Dashboard**

2.16 WHEN displaying father status in the dashboard THEN the system SHALL show a status dictionary/table that includes: status name, definition/description, and type (AI action vs State)

2.17 WHEN logging workflow state transitions THEN the system SHALL include both the WorkflowState and any relevant sub-state or action context to enable better debugging

2.18 WHEN the dashboard displays the current status THEN it SHALL show meaningful context beyond just the state name, such as "Waiting - Quality Time scheduled for tomorrow at 3pm"

### Unchanged Behavior (Regression Prevention)

**Bug 1: Duplicate Message Processing**

3.1 WHEN messages with different content are received from the same user in quick succession THEN the system SHALL CONTINUE TO process each message independently as legitimate distinct messages

3.2 WHEN a message fails processing and is retried by the user (intentionally) after a significant time gap THEN the system SHALL CONTINUE TO process it as a new message

**Bug 2: Generic Error Responses**

3.3 WHEN a critical security-related error occurs THEN the system SHALL CONTINUE TO return a generic error message without exposing internal system details to the user

3.4 WHEN normal message processing completes successfully THEN the system SHALL CONTINUE TO return the AI-generated or template-based response without modification

**Bug 3: Date/Time Calculation**

3.5 WHEN the father's timezone is correctly configured THEN the system SHALL CONTINUE TO display times according to that timezone for all message types

3.6 WHEN storing timestamps in the database THEN the system SHALL CONTINUE TO store them in UTC format for consistency

**Bug 4: QUALITY_TIME_FOLLOW_UP State**

3.7 WHEN the father is in WAITING state with upcoming Quality Time THEN the system SHALL CONTINUE TO send morning reminders about the scheduled event

3.8 WHEN a father completes their feedback in QUALITY_TIME_FOLLOW_UP THEN the system SHALL CONTINUE TO transition to SCHEDULE_QUALITY_TIME to schedule the next session

**Bug 5: User Frustration Handler**

3.9 WHEN a user sends a normal message without frustration indicators THEN the system SHALL CONTINUE TO process it through the standard pattern matching and state handling flow

3.10 WHEN a user expresses frustration but also provides actionable content THEN the system SHALL CONTINUE TO process the actionable content while adding empathetic acknowledgment

**Bug 6: Status Dictionary**

3.11 WHEN the workflow state machine transitions between states THEN the system SHALL CONTINUE TO use the existing WorkflowState enum values for core state management

3.12 WHEN processing messages in WAITING state THEN the system SHALL CONTINUE TO use the existing pattern matching and state handler logic

## Bug Condition Summary

### Bug Condition Functions (Pseudocode)

```pascal
// Bug 1: Duplicate Message Processing
FUNCTION isDuplicateMessageBug(msg)
  INPUT: msg of type InboundMessage
  OUTPUT: boolean
  
  RETURN (existsRecentMessage(msg.sender, msg.content, 60_SECONDS) 
          AND msg.idempotencyKey NOT IN processedKeys)
END FUNCTION

// Bug 2: Generic Error Response
FUNCTION isGenericErrorBug(error, state)
  INPUT: error of type Exception, state of type WorkflowState
  OUTPUT: boolean
  
  RETURN (error IS NOT NULL 
          AND response = "משהו השתבש" 
          AND fallbackTemplateExists(state))
END FUNCTION

// Bug 3: Date Calculation Error  
FUNCTION isDateCalculationBug(displayedDate, fatherTimezone)
  INPUT: displayedDate of type String, fatherTimezone of type ZoneId
  OUTPUT: boolean
  
  actualTomorrow ← LocalDate.now(fatherTimezone).plusDays(1)
  RETURN displayedDate.contains("מחר") AND extractDate(displayedDate) ≠ actualTomorrow
END FUNCTION

// Bug 4: Follow-up State Bug
FUNCTION isFollowUpStateBug(father, response)
  INPUT: father of type Father, response of type String
  OUTPUT: boolean
  
  RETURN (father.currentState = QUALITY_TIME_FOLLOW_UP 
          AND (response.referencesUpcomingQT() OR NOT response.asksAboutCompletedQT()))
END FUNCTION

// Bug 5: Frustration Handler Missing
FUNCTION isFrustrationHandlerBug(message, response)
  INPUT: message of type String, response of type String
  OUTPUT: boolean
  
  frustrationPatterns ← ["why again", "repeat", "already said", "למה שוב", "כבר אמרתי"]
  RETURN (containsAny(message, frustrationPatterns) 
          AND NOT response.containsEmpathy())
END FUNCTION

// Bug 6: Vague Waiting State
FUNCTION isVagueWaitingStateBug(dashboardStatus)
  INPUT: dashboardStatus of type StatusDisplay
  OUTPUT: boolean
  
  RETURN (dashboardStatus.state = "WAITING" 
          AND dashboardStatus.context = NULL)
END FUNCTION
```

### Property Specifications

```pascal
// Property: Fix Checking - Bug 1
FOR ALL msg WHERE isDuplicateMessage(msg) DO
  result ← processMessage'(msg)
  ASSERT result = cachedResponse(msg) AND processedOnce(msg)
END FOR

// Property: Fix Checking - Bug 2  
FOR ALL (error, state) WHERE hasProcessingError(error) DO
  result ← handleError'(error, state)
  ASSERT result.hasStateSpecificGuidance() AND result.hasDebugContext()
END FOR

// Property: Fix Checking - Bug 3
FOR ALL (father, date) WHERE displayingDate(father, date) DO
  result ← formatDate'(date, father.timezone)
  ASSERT result.dayOfWeek = actualDayOfWeek(date, father.timezone)
END FOR

// Property: Fix Checking - Bug 4
FOR ALL father WHERE father.state = QUALITY_TIME_FOLLOW_UP DO
  result ← generateFollowUpMessage'(father)
  ASSERT result.referencesCompletedQT() AND result.asksForFeedback()
END FOR

// Property: Fix Checking - Bug 5
FOR ALL msg WHERE containsFrustrationIndicator(msg) DO
  result ← processMessage'(msg)
  ASSERT result.containsEmpathy() AND result.continuesWorkflow()
END FOR

// Property: Fix Checking - Bug 6
FOR ALL status WHERE status.state = WAITING DO
  result ← formatStatusDisplay'(status)
  ASSERT result.hasContextualDescription() AND result.hasScheduleInfo()
END FOR

// Property: Preservation Checking - All Bugs
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X) = F'(X)
END FOR
```
