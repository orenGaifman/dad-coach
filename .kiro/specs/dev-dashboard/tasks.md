# Implementation Plan: Dev Dashboard

## Overview

This implementation plan creates a debugging dashboard for the Dad Coach application, providing developers and QA engineers with real-time visibility into WhatsApp workflow conversations. The implementation is split between the Java/Spring Boot backend (dad-coach) and the TypeScript/Next.js frontend (dad-coach-web).

## Tasks

- [x] 1. Backend - Core Infrastructure and Security
  - [x] 1.1 Create DevEnvironmentGuard component
    - Create `com.dadcoach.api.dev.DevEnvironmentGuard` component
    - Implement environment detection logic with priority: explicit config > Spring profile
    - Implement `isDevAllowed()` method checking `dadcoach.dev.enabled` property and Spring profiles
    - Implement `requireDevAccess()` method that throws exception if access denied
    - Block access when profile is "prod" or "production" (case-insensitive)
    - Block access on any exception as security precaution
    - Add logging for rejected requests
    - _Requirements: 5.1, 5.2, 5.3, 12.1, 12.2, 12.3, 12.4, 12.5_

  - [x] 1.2 Create DevEndpointsDisabledException and error handling
    - Create `DevEndpointsDisabledException` runtime exception class
    - Create `DevExceptionHandler` with `@RestControllerAdvice`
    - Handle `DevEndpointsDisabledException` returning HTTP 403 with sanitized message
    - Handle `FatherNotFoundException` returning HTTP 404
    - Handle `ConstraintViolationException` returning HTTP 400
    - Ensure no stack traces or internal details in production rejection responses
    - _Requirements: 5.4_

  - [ ]* 1.3 Write unit tests for DevEnvironmentGuard
    - Test explicit `dadcoach.dev.enabled=true` allows access
    - Test explicit `dadcoach.dev.enabled=false` blocks access
    - Test "prod" profile blocks access when property not set
    - Test "production" profile blocks access (case-insensitive)
    - Test non-production profiles (dev, local, staging, test, qa) allow access
    - Test exception handling blocks access as security precaution
    - **Property 9: Environment Detection Priority**
    - **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5**

- [x] 2. Backend - DTOs and Data Models
  - [x] 2.1 Create response DTOs for Dev API
    - Create `FatherListItemDto` record with id, displayName, phone, status, currentWorkflowState, previousWorkflowState, currentBelt, lastInteractionAt
    - Create `FatherStateDetailsDto` record with nested WorkflowInfo and BeltInfo records
    - Create `MessageDto` record with id, direction, content, createdAt
    - Create `TransitionDto` record with id, fromState, toState, triggerReason, triggerMessageId, createdAt
    - Create `ChildDto` record with id, name, birthDate
    - Create `QualityTimeDto` record with id, childName, scheduledStart, scheduledEnd, status
    - Create `PaginatedResponse<T>` generic record with items, page, pageSize, totalItems, totalPages, links
    - Create `ErrorResponse` record for error responses
    - _Requirements: 1.1, 2.1, 2.2, 2.3, 3.1, 4.1, 13.1, 13.2, 13.3_

  - [ ]* 2.2 Write unit tests for DTO serialization
    - Test timestamp fields serialize to ISO 8601 with timezone offset
    - Test enum fields serialize as uppercase strings
    - Test JSON round-trip preserves all fields for each DTO type
    - **Property 10: Serialization Format Correctness**
    - **Property 11: JSON Round-Trip Preservation**
    - **Validates: Requirements 13.1, 13.2, 13.4**

- [x] 3. Backend - DevService Implementation
  - [x] 3.1 Create DevService with father listing
    - Create `com.dadcoach.api.dev.DevService` class with repository dependencies
    - Implement `listFathers(String search, Pageable pageable)` method
    - Implement case-insensitive search filtering by phone or display_name
    - Order results by lastInteractionAt descending
    - Map entities to FatherListItemDto
    - _Requirements: 1.1, 1.2, 1.5_

  - [x] 3.2 Implement getFatherState method
    - Implement `getFatherState(Long fatherId)` method
    - Query father, children, and quality_time data
    - Implement partial data handling with error accumulation
    - Return FatherStateDetailsDto with partial flag and errors list when queries fail
    - Throw FatherNotFoundException if father doesn't exist
    - _Requirements: 2.1, 2.2, 2.3, 2.5, 2.6_

  - [x] 3.3 Implement getMessages method
    - Implement `getMessages(Long fatherId, int limit, Instant since)` method
    - Query message_log table ordered by created_at descending
    - Apply limit constraint (max 200)
    - Filter by since timestamp when provided
    - Throw FatherNotFoundException if father doesn't exist
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.6_

  - [x] 3.4 Implement getTransitions method
    - Implement `getTransitions(Long fatherId, int limit)` method
    - Query workflow_state_transition_log ordered by created_at descending
    - Apply limit constraint (max 100)
    - Throw FatherNotFoundException if father doesn't exist
    - _Requirements: 4.1, 4.2, 4.3, 4.5_

  - [ ]* 3.5 Write unit tests for DevService
    - Test search filtering correctness (phone and display_name)
    - Test limit enforcement for all endpoints
    - Test temporal ordering (descending by timestamp)
    - Test related data inclusion for children and quality times
    - Test since timestamp filtering for messages
    - Test partial data handling when queries fail
    - **Property 1: Search Filtering Correctness**
    - **Property 2: Limit Enforcement**
    - **Property 3: Temporal Ordering Invariant**
    - **Property 4: Related Data Inclusion Completeness**
    - **Property 5: Timestamp Filtering Correctness**
    - **Validates: Requirements 1.2, 1.3, 1.5, 2.2, 2.3, 2.6, 3.2, 3.3, 3.4, 4.2, 4.3**

- [x] 4. Backend - DevController Implementation
  - [x] 4.1 Create DevController with list fathers endpoint
    - Create `com.dadcoach.api.dev.DevController` class
    - Inject DevEnvironmentGuard and DevService
    - Implement `GET /api/v1/dev/fathers` endpoint
    - Add search, page, page_size query parameters
    - Validate page_size max 100, reject with HTTP 400 if exceeded
    - Call environmentGuard.requireDevAccess() at start of each endpoint
    - Return PaginatedResponse with _links for pagination
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 5.1_

  - [x] 4.2 Implement get father state endpoint
    - Implement `GET /api/v1/dev/fathers/{id}/state` endpoint
    - Call environmentGuard.requireDevAccess()
    - Return FatherStateDetailsDto
    - Handle 404 when father not found
    - _Requirements: 2.1, 2.4, 2.5_

  - [x] 4.3 Implement get messages endpoint
    - Implement `GET /api/v1/dev/fathers/{id}/messages` endpoint
    - Add limit and since query parameters
    - Validate limit max 200, reject with HTTP 400 if exceeded
    - Call environmentGuard.requireDevAccess()
    - Return list of MessageDto
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.6_

  - [x] 4.4 Implement get transitions endpoint
    - Implement `GET /api/v1/dev/fathers/{id}/transitions` endpoint
    - Add limit query parameter
    - Validate limit max 100, reject with HTTP 400 if exceeded
    - Call environmentGuard.requireDevAccess()
    - Return list of TransitionDto
    - _Requirements: 4.1, 4.2, 4.4, 4.5_

  - [ ]* 4.5 Write integration tests for DevController
    - Test all endpoints return 403 when DevEnvironmentGuard blocks
    - Test page_size > 100 returns 400
    - Test limit > max returns 400 for messages and transitions
    - Test 404 for non-existent father (when not in production)
    - Test 403 takes precedence over 404 in production
    - Test successful responses with valid data
    - **Property 6: Error Response Sanitization**
    - **Validates: Requirements 1.3, 1.4, 2.4, 2.5, 3.2, 3.5, 3.6, 4.2, 4.4, 4.5, 5.1, 5.4**

- [x] 5. Checkpoint - Backend Implementation Complete
  - Ensure all backend tests pass
  - Verify all four Dev API endpoints are functional
  - Ask the user if questions arise

- [x] 6. Frontend - Type Definitions and API Client
  - [x] 6.1 Create TypeScript interfaces for Dev Dashboard
    - Create `/src/types/dev.ts` with DevFatherListItem interface
    - Create DevFatherState, DevChild, DevQualityTime interfaces
    - Create DevMessage, DevTransition interfaces
    - Create PaginatedResponse generic type
    - _Requirements: 1.1, 2.1, 3.1, 4.1_

  - [x] 6.2 Create Dev API client functions
    - Create `/src/api/dev.ts` with fetch functions
    - Implement `fetchFathers(search?, page?, pageSize?)` function
    - Implement `fetchFatherState(id)` function
    - Implement `fetchMessages(id, limit?, since?)` function
    - Implement `fetchTransitions(id, limit?)` function
    - Handle error responses and type casting
    - _Requirements: 1.1, 2.1, 3.1, 4.1_

- [x] 7. Frontend - Dashboard Layout and Father Selector
  - [x] 7.1 Create Dev Dashboard page structure
    - Create `/app/dev/dashboard/page.tsx` as main dashboard page
    - Create `/app/dev/dashboard/layout.tsx` with dev-specific layout
    - Implement redirect from `/dev` to `/dev/dashboard`
    - Add "Development Only" warning banner at top
    - Set up responsive grid layout (40% left, 60% right columns)
    - _Requirements: 14.1, 14.2, 14.3, 14.4_

  - [x] 7.2 Create FatherSelector component
    - Create `/app/dev/dashboard/components/FatherSelector.tsx`
    - Implement searchable dropdown/input field
    - Implement 300ms debounce on search input
    - Filter by phone number or display_name
    - Store selected father ID in localStorage
    - Auto-load stored father ID on page load
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [ ]* 7.3 Write unit tests for FatherSelector
    - Test debounce behavior (300ms delay)
    - Test localStorage persistence
    - Test auto-load on mount
    - _Requirements: 6.2, 6.4, 6.5_

- [x] 8. Frontend - Father State Display
  - [x] 8.1 Create FatherStatePanel component
    - Create `/app/dev/dashboard/components/FatherStatePanel.tsx`
    - Display current_workflow_state with color-coded badge
    - Display previous_workflow_state for context
    - Display workflow_state_entered_at in Israel timezone
    - Display current_belt with belt color indicator
    - Display phone, display_name, status
    - Display children list with empty state handling
    - Show partial data indicator when _partial is true
    - Show error indicators for failed components
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

  - [x] 8.2 Create timezone display utilities
    - Create `/src/utils/timezone.ts` with Israel timezone conversion
    - Implement `formatIsraelDate(timestamp)` returning DD/MM/YYYY format
    - Implement `formatIsraelTime(timestamp)` returning HH:mm:ss format
    - Implement `formatIsraelDateTime(timestamp)` combining both
    - Handle both UTC+2 and UTC+3 (daylight saving) correctly
    - _Requirements: 10.1, 10.2, 10.3_

  - [x] 8.3 Create TimezoneIndicator component
    - Create `/app/dev/dashboard/components/TimezoneIndicator.tsx`
    - Display "Asia/Jerusalem" timezone indicator
    - Show error message if timezone info unavailable
    - _Requirements: 10.4, 10.5_

  - [ ]* 8.4 Write unit tests for timezone utilities
    - Test UTC to Israel timezone conversion
    - Test DD/MM/YYYY date format
    - Test HH:mm:ss time format
    - Test daylight saving time handling (UTC+2 vs UTC+3)
    - **Property 7: Timezone Conversion Correctness**
    - **Property 8: Display Formatting Consistency**
    - **Validates: Requirements 10.1, 10.2, 10.3**

- [x] 9. Frontend - Message Log Panel
  - [x] 9.1 Create MessageLogPanel component
    - Create `/app/dev/dashboard/components/MessageLogPanel.tsx`
    - Display messages in chat-style conversation view
    - Visually distinguish INBOUND (from father) and OUTBOUND (from bot) messages
    - Display created_at timestamp in Israel timezone HH:mm:ss format
    - Add manual refresh button
    - Display last refresh timestamp
    - _Requirements: 8.1, 8.2, 8.3, 8.6_

  - [x] 9.2 Implement message polling logic
    - Implement polling hook with 2-second interval
    - Track `since` timestamp for incremental fetching
    - Prepend new messages to list without full refresh
    - Respect auto-refresh toggle state
    - Allow in-flight requests to complete when disabled
    - _Requirements: 8.4, 8.5, 11.3_

- [x] 10. Frontend - Transition Timeline
  - [x] 10.1 Create TransitionTimeline component
    - Create `/app/dev/dashboard/components/TransitionTimeline.tsx`
    - Display transitions as timeline view
    - Show from_state → to_state with arrow indicator
    - Display trigger_reason for each transition
    - Make trigger_message_id clickable reference when present
    - Display created_at in Israel timezone
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.6_

  - [x] 10.2 Implement transition polling logic
    - Implement polling with 3-second interval
    - Refresh full transition list on each poll
    - Respect auto-refresh toggle state
    - _Requirements: 9.5, 11.3_

- [x] 11. Frontend - Auto-Refresh Control
  - [x] 11.1 Create AutoRefreshToggle component
    - Create `/app/dev/dashboard/components/AutoRefreshToggle.tsx`
    - Implement toggle switch for enable/disable
    - Show "Live" badge or spinning icon when enabled
    - Default to enabled on page load
    - Provide shared state for polling components
    - _Requirements: 11.1, 11.2, 11.4_

  - [x] 11.2 Wire up auto-refresh state
    - Create context or hook for shared auto-refresh state
    - Connect MessageLogPanel polling to auto-refresh state
    - Connect TransitionTimeline polling to auto-refresh state
    - Display last refresh timestamp for each section
    - _Requirements: 11.3, 11.5_

- [x] 12. Frontend - Integration and Error Handling
  - [x] 12.1 Integrate all dashboard components
    - Wire FatherSelector to load data into all panels
    - Implement loading states for each panel
    - Implement error states with retry options
    - Handle partial data responses (show available data with warnings)
    - Ensure components work together seamlessly
    - _Requirements: 6.3, 7.7_

  - [x] 12.2 Add DevWarningBanner component
    - Create `/app/dev/dashboard/components/DevWarningBanner.tsx`
    - Display prominent "Development Only" warning
    - Style to be clearly visible but not obtrusive
    - _Requirements: 14.4_

  - [ ]* 12.3 Write integration tests for dashboard
    - Test father selection loads all panels
    - Test auto-refresh toggle stops/starts polling
    - Test error handling displays appropriate messages
    - Test partial data displays with warnings
    - _Requirements: 6.3, 7.7, 11.3_

- [x] 13. Final Checkpoint - Full Integration
  - Ensure all frontend tests pass
  - Verify dashboard works end-to-end with backend
  - Test in non-production environment
  - Ask the user if questions arise

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Backend implementation (tasks 1-4) should be completed before frontend (tasks 6-12)
- The backend is in the `dad-coach` repository, frontend is in `dad-coach-web`

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "6.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "2.2", "6.2"] },
    { "id": 2, "tasks": ["3.1", "7.1"] },
    { "id": 3, "tasks": ["3.2", "3.3", "3.4", "7.2", "8.2"] },
    { "id": 4, "tasks": ["3.5", "4.1", "7.3", "8.1", "8.3"] },
    { "id": 5, "tasks": ["4.2", "4.3", "4.4", "8.4", "9.1"] },
    { "id": 6, "tasks": ["4.5", "9.2", "10.1"] },
    { "id": 7, "tasks": ["10.2", "11.1"] },
    { "id": 8, "tasks": ["11.2", "12.1", "12.2"] },
    { "id": 9, "tasks": ["12.3"] }
  ]
}
```
