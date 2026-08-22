# Implementation Plan

## Overview

This implementation plan addresses six interrelated bugs identified from WhatsApp chatbot conversation logs in the Dad Coach application:

1. **Bug 1: Duplicate Message Processing** - Messages processed multiple times due to webhook retries
2. **Bug 2: Generic Error Responses** - Unhelpful "משהו השתבש" errors without context
3. **Bug 3: Date/Time Calculation Error** - "Tomorrow" incorrectly showing today's date
4. **Bug 4: QUALITY_TIME_FOLLOW_UP State Bug** - Follow-up asking about future instead of completed QT
5. **Bug 5: Missing Frustration Handler** - No empathetic response to user frustration
6. **Bug 6: Status Dictionary for Dashboard** - WAITING state too vague for debugging

Each bug follows the exploratory bugfix workflow: write exploration tests to confirm the bug, write preservation tests to protect existing behavior, implement the fix, then verify both tests pass.

## Task Dependency Graph

```json
{
  "waves": [
    {
      "name": "Exploration Tests",
      "tasks": ["1", "5", "9", "13", "17", "21"]
    },
    {
      "name": "Preservation Tests",
      "tasks": ["2", "6", "10", "14", "18", "22"]
    },
    {
      "name": "Bug 1 Implementation",
      "tasks": ["3", "4"],
      "dependsOn": ["1", "2"]
    },
    {
      "name": "Bug 2 Implementation",
      "tasks": ["7", "8"],
      "dependsOn": ["5", "6"]
    },
    {
      "name": "Bug 3 Implementation",
      "tasks": ["11", "12"],
      "dependsOn": ["9", "10"]
    },
    {
      "name": "Bug 4 Implementation",
      "tasks": ["15", "16"],
      "dependsOn": ["13", "14"]
    },
    {
      "name": "Bug 5 Implementation",
      "tasks": ["19", "20"],
      "dependsOn": ["17", "18"]
    },
    {
      "name": "Bug 6 Implementation",
      "tasks": ["23", "24"],
      "dependsOn": ["21", "22"]
    },
    {
      "name": "Integration & Checkpoint",
      "tasks": ["25", "26"],
      "dependsOn": ["4", "8", "12", "16", "20", "24"]
    }
  ]
}
```

## Tasks

### Bug 1: Duplicate Message Processing

- [x] 1. Write bug condition exploration test for duplicate message detection
  - **Property 1: Bug Condition** - Duplicate Message Processing
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **GOAL**: Surface counterexamples that demonstrate duplicate messages are processed multiple times
  - **Scoped PBT Approach**: Test with identical messages from same sender within 60-second window
  - Test that sending identical message content twice within 60 seconds results in both being processed (bug demonstration)
  - Test that messages with different webhook delivery IDs but same content bypass duplicate detection
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (both messages are processed, confirming the bug exists)
  - Document counterexamples: e.g., "כן" sent at 18:01:55 and 18:02:18 both processed
  - _Requirements: 1.1, 1.2, 1.3_

- [x] 2. Write preservation property tests for distinct message processing (BEFORE implementing fix)
  - **Property 2: Preservation** - Distinct Messages Must Be Processed Independently
  - **IMPORTANT**: Follow observation-first methodology
  - Observe: Different messages from same user in quick succession are both processed on unfixed code
  - Observe: Intentional user retries after significant time gap are processed on unfixed code
  - Write property-based test: for all non-duplicate messages (different content OR different sender OR outside time window), both messages are processed independently
  - Verify test passes on UNFIXED code
  - _Requirements: 3.1, 3.2_

- [x] 3. Implement duplicate message detection fix

  - [x] 3.1 Add content fingerprint cache to WorkflowIdempotencyService
    - Add `Map<String, CachedResponse> contentFingerprintCache` with 60-second TTL
    - Implement `generateContentFingerprint(sender, content)` using SHA-256 hash
    - Update `checkDuplicate()` to check both idempotency key AND content fingerprint
    - Update `recordProcessed()` to store in both caches
    - _Bug_Condition: isDuplicateMessage(msg) where existsRecentMessage(msg.sender, msg.content, 60_SECONDS)_
    - _Expected_Behavior: Return cached response without reprocessing_
    - _Preservation: Different content or different sender or outside time window → process normally_
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.2 Update WorkflowEngineImpl to pass additional parameters
    - Update idempotency check call to include sender and content parameters
    - Update recordProcessed call to include sender and content parameters
    - Add logging for duplicate detection events
    - _Requirements: 2.1, 2.3_

  - [x] 3.3 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Duplicate Messages Return Cached Response
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (duplicate messages now return cached response)
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.4 Verify preservation tests still pass
    - **Property 2: Preservation** - Distinct Messages Must Be Processed Independently
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (no regressions for distinct messages)
    - Confirm all tests still pass after fix

- [x] 4. Write unit tests for Bug 1
  - Test content fingerprint generation consistency (same input → same hash)
  - Test duplicate detection with timing scenarios (within/outside 60-second window)
  - Test cache expiration behavior
  - Test idempotency key + fingerprint combination
  - _Requirements: 2.1, 2.2, 2.3_

---

### Bug 2: Generic Error Responses

- [x] 5. Write bug condition exploration test for generic error handling
  - **Property 1: Bug Condition** - Generic Error Without State-Specific Fallback
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **GOAL**: Surface counterexamples that demonstrate generic errors are returned without context
  - **Scoped PBT Approach**: Inject AI timeout in SCHEDULE_QUALITY_TIME state
  - Test that processing error returns "משהו השתבש" without state-specific guidance
  - Test that error logs are missing father_id, current_workflow_state context
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (generic error returned, incomplete logs)
  - Document counterexamples found
  - _Requirements: 1.4, 1.5, 1.6_

- [x] 6. Write preservation property tests for successful processing (BEFORE implementing fix)
  - **Property 2: Preservation** - Successful Processing Returns Normal Response
  - **IMPORTANT**: Follow observation-first methodology
  - Observe: Successful message processing returns AI/template response on unfixed code
  - Observe: Security errors return generic messages without internals on unfixed code
  - Write property-based test: for all successful processing OR security errors, response is unchanged
  - Verify test passes on UNFIXED code
  - _Requirements: 3.3, 3.4_

- [x] 7. Implement state-specific error handling fix

  - [x] 7.1 Add state-specific error handling in WorkflowEngineImpl
    - Enhance catch block with comprehensive error logging (father_id, state, message, error_type)
    - Add `getStateSpecificErrorResponse(state, locale)` method with switch statement
    - Return state-specific error messages for SCHEDULE_QUALITY_TIME, QUALITY_TIME_FOLLOW_UP, WAITING states
    - Fall back to generic error only when no state-specific fallback exists
    - _Bug_Condition: hasProcessingError(error) AND fallbackExists(state)_
    - _Expected_Behavior: State-specific error message with full context logging_
    - _Preservation: Security errors → generic message, success → normal response_
    - _Requirements: 2.4, 2.5, 2.6_

  - [x] 7.2 Add state-specific error templates to FallbackMessages
    - Add error templates for each state in Hebrew and English
    - Ensure templates provide actionable guidance
    - _Requirements: 2.5_

  - [x] 7.3 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - State-Specific Error With Full Context
    - **IMPORTANT**: Re-run the SAME test from task 5 - do NOT write a new test
    - Run bug condition exploration test from step 5
    - **EXPECTED OUTCOME**: Test PASSES (state-specific error returned with full logging)
    - _Requirements: 2.4, 2.5, 2.6_

  - [x] 7.4 Verify preservation tests still pass
    - **Property 2: Preservation** - Successful Processing Returns Normal Response
    - **IMPORTANT**: Re-run the SAME tests from task 6 - do NOT write new tests
    - Run preservation property tests from step 6
    - **EXPECTED OUTCOME**: Tests PASS (no regressions for successful processing)

- [x] 8. Write unit tests for Bug 2
  - Test state-specific error message selection for each state
  - Test error logging contains all required fields (father_id, state, message)
  - Test fallback chain progression (state-specific → generic)
  - Test locale handling (Hebrew vs English)
  - _Requirements: 2.4, 2.5, 2.6_

---

### Bug 3: Date Calculation Error

- [x] 9. Write bug condition exploration test for date calculation
  - **Property 1: Bug Condition** - Incorrect Tomorrow Calculation
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **GOAL**: Surface counterexamples that demonstrate "tomorrow" shows today's date
  - **Scoped PBT Approach**: Set server to UTC, father timezone to Asia/Jerusalem, near midnight
  - Test that "מחר, יום שישי 22/08" is displayed when today IS Friday 22/08
  - Test midnight boundary scenarios across timezones
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (incorrect date displayed)
  - Document counterexamples found
  - _Requirements: 1.7, 1.8, 1.9_

- [x] 10. Write preservation property tests for correct timezone handling (BEFORE implementing fix)
  - **Property 2: Preservation** - Existing Timezone Handling Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Observe: Fathers with correctly configured timezone get correct time display on unfixed code
  - Observe: Timestamps stored in UTC format on unfixed code
  - Write property-based test: for all non-boundary timezone scenarios, behavior is unchanged
  - Verify test passes on UNFIXED code
  - _Requirements: 3.5, 3.6_

- [x] 11. Implement timezone-aware date calculation fix

  - [x] 11.1 Add timezone-aware date methods to MessageContext
    - Add `getTomorrowInTimezone()` using father's configured ZoneId
    - Add `getTodayInTimezone()` using father's configured ZoneId
    - Add `validateDayOfWeek(date, formattedString)` to verify day matches actual date
    - _Bug_Condition: displayingTomorrow(father) with timezone mismatch_
    - _Expected_Behavior: Tomorrow = LocalDate.now(father.timezone).plusDays(1)_
    - _Preservation: Existing timezone handling unchanged_
    - _Requirements: 2.7, 2.8, 2.9_

  - [x] 11.2 Update FallbackMessages to use timezone-aware calculations
    - Update substitute method to use `context.getTomorrowInTimezone()` for {tomorrow} placeholder
    - Add day-of-week validation before returning formatted date
    - Default to "Asia/Jerusalem" if timezone not configured
    - _Requirements: 2.7, 2.8_

  - [x] 11.3 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Correct Tomorrow Calculation
    - **IMPORTANT**: Re-run the SAME test from task 9 - do NOT write a new test
    - Run bug condition exploration test from step 9
    - **EXPECTED OUTCOME**: Test PASSES (correct date displayed in father's timezone)
    - _Requirements: 2.7, 2.8, 2.9_

  - [x] 11.4 Verify preservation tests still pass
    - **Property 2: Preservation** - Existing Timezone Handling Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 10 - do NOT write new tests
    - Run preservation property tests from step 10
    - **EXPECTED OUTCOME**: Tests PASS (no regressions for existing timezone handling)

- [x] 12. Write unit tests for Bug 3
  - Test timezone-aware date calculation across various timezones
  - Test day-of-week validation
  - Test midnight boundary scenarios (UTC vs Asia/Jerusalem)
  - Test default timezone fallback
  - _Requirements: 2.7, 2.8, 2.9_

---

### Bug 4: QUALITY_TIME_FOLLOW_UP State Bug

- [x] 13. Write bug condition exploration test for follow-up state
  - **Property 1: Bug Condition** - Follow-Up References Future Instead of Completed QT
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **GOAL**: Surface counterexamples that demonstrate follow-up asks about future QT
  - **Scoped PBT Approach**: Create QT that ended 1 hour ago, put father in FOLLOW_UP state
  - Test that follow-up message references UPCOMING scheduled QT instead of completed one
  - Test that response asks about future ("Ready for your Quality Time tomorrow?") in FOLLOW_UP state
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (upcoming QT referenced instead of completed)
  - Document counterexamples found
  - _Requirements: 1.10, 1.11, 1.12_

- [x] 14. Write preservation property tests for other state handling (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-Follow-Up States Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Observe: WAITING state sends morning reminders about scheduled events on unfixed code
  - Observe: Completed feedback transitions to SCHEDULE_QUALITY_TIME on unfixed code
  - Write property-based test: for all fathers NOT in QUALITY_TIME_FOLLOW_UP, QT selection unchanged
  - Verify test passes on UNFIXED code
  - _Requirements: 3.7, 3.8_

- [x] 15. Implement follow-up state fix

  - [x] 15.1 Fix findQualityTimeForFollowUp in FollowUpStateHandler
    - Change filter from `status = "SCHEDULED"` to `end_time < now`
    - Find most recent QT that has ENDED (end_time < now)
    - Remove fallback to `getNextScheduledQualityTime()` which returns upcoming events
    - Add logging when no completed QT found for follow-up
    - _Bug_Condition: father.state = QUALITY_TIME_FOLLOW_UP AND response.referencesUpcomingQT()_
    - _Expected_Behavior: qt.endTime < now AND response.referencesCompletedQT(qt)_
    - _Preservation: Non-FOLLOW_UP states use existing QT selection logic_
    - _Requirements: 2.10, 2.11, 2.12_

  - [x] 15.2 Update handleMarkCompleted to use correct QT
    - Use `findQualityTimeForFollowUp()` instead of `getNextScheduledQualityTime()`
    - Handle case where no completed QT exists (transition to schedule with message)
    - _Requirements: 2.11, 2.12_

  - [x] 15.3 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Follow-Up References Completed QT
    - **IMPORTANT**: Re-run the SAME test from task 13 - do NOT write a new test
    - Run bug condition exploration test from step 13
    - **EXPECTED OUTCOME**: Test PASSES (completed QT referenced in follow-up)
    - _Requirements: 2.10, 2.11, 2.12_

  - [x] 15.4 Verify preservation tests still pass
    - **Property 2: Preservation** - Non-Follow-Up States Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 14 - do NOT write new tests
    - Run preservation property tests from step 14
    - **EXPECTED OUTCOME**: Tests PASS (no regressions for other states)

- [x] 16. Write unit tests for Bug 4
  - Test findQualityTimeForFollowUp returns ended QT (end_time < now)
  - Test correct QT is referenced in follow-up message
  - Test edge case: multiple ended QTs (should return most recent)
  - Test edge case: no ended QTs available
  - _Requirements: 2.10, 2.11, 2.12_

---

### Bug 5: Frustration Handler

- [x] 17. Write bug condition exploration test for frustration handling
  - **Property 1: Bug Condition** - No Empathy for Frustrated Users
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **GOAL**: Surface counterexamples that demonstrate bot ignores user frustration
  - **Scoped PBT Approach**: Send frustration messages in various states
  - Test "כבר אמרתי לך שכן" returns response without empathy acknowledgment
  - Test "why are you asking again" in SCHEDULE_QUALITY_TIME returns generic response
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (no empathy in response to frustration)
  - Document counterexamples found
  - _Requirements: 1.13, 1.14, 1.15_

- [x] 18. Write preservation property tests for standard message handling (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-Frustration Messages Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Observe: Normal messages without frustration indicators processed through standard flow on unfixed code
  - Observe: Messages with frustration + actionable content still process the actionable content on unfixed code
  - Write property-based test: for all messages WITHOUT frustration indicators, processing unchanged
  - Verify test passes on UNFIXED code
  - _Requirements: 3.9, 3.10_

- [ ] 19. Implement frustration handler fix

  - [-] 19.1 Add frustration patterns to StatePatterns
    - Add `FRUSTRATION_PATTERNS` list with English patterns ("why again", "repeat", "already said")
    - Add Hebrew patterns ("למה שוב", "כבר אמרתי", "שאלת כבר", "אתה שואל שוב")
    - Map to new `WorkflowAction.ACKNOWLEDGE_FRUSTRATION` action
    - _Bug_Condition: containsFrustrationIndicator(msg) AND NOT response.containsEmpathy()_
    - _Expected_Behavior: response.containsEmpathy() AND response.continuesWorkflow()_
    - _Preservation: Non-frustration messages use standard pattern matching_
    - _Requirements: 2.13, 2.14, 2.15_

  - [~] 19.2 Add ACKNOWLEDGE_FRUSTRATION to WorkflowAction enum
    - Add new enum value for frustration acknowledgment
    - _Requirements: 2.13_

  - [~] 19.3 Add frustration handling to WorkflowEngineImpl
    - Check for frustration patterns FIRST before state-specific patterns
    - Add `getEmpathyMessage(locale)` method for Hebrew/English empathy prefixes
    - Prepend empathy message to normal workflow response
    - _Requirements: 2.13, 2.14, 2.15_

  - [~] 19.4 Add FRUSTRATION_ACKNOWLEDGMENT templates to FallbackMessages
    - Add empathy message templates in Hebrew and English
    - _Requirements: 2.14_

  - [~] 19.5 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Empathetic Response to Frustration
    - **IMPORTANT**: Re-run the SAME test from task 17 - do NOT write a new test
    - Run bug condition exploration test from step 17
    - **EXPECTED OUTCOME**: Test PASSES (empathy acknowledgment in response)
    - _Requirements: 2.13, 2.14, 2.15_

  - [~] 19.6 Verify preservation tests still pass
    - **Property 2: Preservation** - Non-Frustration Messages Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 18 - do NOT write new tests
    - Run preservation property tests from step 18
    - **EXPECTED OUTCOME**: Tests PASS (no regressions for standard messages)

- [~] 20. Write unit tests for Bug 5
  - Test frustration pattern detection in English
  - Test frustration pattern detection in Hebrew
  - Test empathy prefix is added to response in correct locale
  - Test frustration + actionable content processes both
  - _Requirements: 2.13, 2.14, 2.15_

---

### Bug 6: Status Dictionary Dashboard

- [~] 21. Write bug condition exploration test for dashboard status display
  - **Property 1: Bug Condition** - Vague WAITING Status Without Context
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **GOAL**: Surface counterexamples that demonstrate WAITING shows only state name
  - **Scoped PBT Approach**: View father in WAITING state in dashboard
  - Test that dashboard shows only "WAITING" without contextual description
  - Test that no status dictionary panel exists
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (only state name shown, no dictionary)
  - Document counterexamples found
  - _Requirements: 1.16, 1.17, 1.18_

- [~] 22. Write preservation property tests for existing dashboard functionality (BEFORE implementing fix)
  - **Property 2: Preservation** - Existing Dashboard Functionality Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Observe: Father selection works on unfixed dashboard
  - Observe: Auto-refresh works on unfixed dashboard
  - Observe: State display shows current state on unfixed dashboard
  - Write property-based test: for all dashboard interactions, existing functionality unchanged
  - Verify test passes on UNFIXED code
  - _Requirements: 3.11, 3.12_

- [ ] 23. Implement status dictionary dashboard fix

  - [~] 23.1 Create StatusDictionaryPanel component
    - Create new file `dad-coach-web/app/dev/dashboard/components/StatusDictionaryPanel.tsx`
    - Define `STATUS_DEFINITIONS` array with all workflow states (WELCOME, SCHEDULE_QUALITY_TIME, WAITING, QUALITY_TIME_FOLLOW_UP, ACTIVITY_IDEAS)
    - Include displayName, description, type, and possibleActions for each state
    - Render as collapsible/expandable table with state, description, and actions columns
    - _Bug_Condition: dashboardStatus.state = "WAITING" AND dashboardStatus.context = NULL_
    - _Expected_Behavior: hasStatusDictionary() AND hasContextualDescription()_
    - _Preservation: Existing dashboard functionality unchanged_
    - _Requirements: 2.16, 2.17, 2.18_

  - [~] 23.2 Add StatusDictionaryPanel to dashboard page
    - Import StatusDictionaryPanel in `dad-coach-web/app/dev/dashboard/page.tsx`
    - Add component to dashboard layout after existing panels
    - _Requirements: 2.16_

  - [~] 23.3 Add contextual status description to FatherStatePanel
    - Add `getStatusContext(state, nextQTTime)` function
    - Return contextual descriptions: "Waiting - QT scheduled for {time}", "Following up on completed Quality Time", etc.
    - Update status display to show context alongside state name
    - _Requirements: 2.17, 2.18_

  - [~] 23.4 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Contextual Status With Dictionary
    - **IMPORTANT**: Re-run the SAME test from task 21 - do NOT write a new test
    - Run bug condition exploration test from step 21
    - **EXPECTED OUTCOME**: Test PASSES (status dictionary present, contextual descriptions shown)
    - _Requirements: 2.16, 2.17, 2.18_

  - [~] 23.5 Verify preservation tests still pass
    - **Property 2: Preservation** - Existing Dashboard Functionality Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 22 - do NOT write new tests
    - Run preservation property tests from step 22
    - **EXPECTED OUTCOME**: Tests PASS (no regressions for existing functionality)

- [~] 24. Write unit tests for Bug 6
  - Test StatusDictionaryPanel renders all workflow states
  - Test contextual description generation for each state
  - Test dashboard integration with new component
  - Test responsive layout and styling
  - _Requirements: 2.16, 2.17, 2.18_

---

### Integration Tests

- [~] 25. Write integration tests for all bug fixes
  - Test full flow: duplicate message handling end-to-end (send duplicate, verify single processing)
  - Test full flow: error handling with state recovery (inject error, verify state-specific response)
  - Test full flow: date display across timezone boundaries (verify correct tomorrow in father's TZ)
  - Test full flow: QUALITY_TIME_FOLLOW_UP state transition and message generation
  - Test full flow: frustration detection and empathetic response in conversation
  - Test dashboard: render with status dictionary and verify all components display correctly
  - _Requirements: All_

---

### Final Checkpoint

- [~] 26. Checkpoint - Ensure all tests pass
  - Run all unit tests for all 6 bug fixes
  - Run all property-based tests (exploration and preservation)
  - Run all integration tests
  - Verify no regressions in existing functionality
  - Ensure all tests pass, ask the user if questions arise

## Notes

- **Bug 1-5** are backend changes in the `dad-coach` Java project
- **Bug 6** is a frontend change in the `dad-coach-web` Next.js project
- Each bug follows the exploratory workflow: exploration test → preservation test → implementation → verification
- Exploration tests should FAIL on unfixed code (confirming the bug exists) and PASS after the fix
- Preservation tests should PASS on both unfixed and fixed code (confirming no regressions)
- Property-based testing is recommended for stronger guarantees on both exploration and preservation
- The bugs can be worked on in parallel as they affect different components
