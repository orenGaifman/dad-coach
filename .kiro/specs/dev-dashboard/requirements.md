# Requirements Document

## Introduction

The Dev Dashboard is a debugging tool for developers and QA engineers to gain real-time visibility into the WhatsApp workflow conversation flow of the Dad Coach application. When testing the WhatsApp-based coaching interactions, developers need to see what's happening "under the hood" — the current workflow state, message history, state transitions, and why specific responses were generated. This dashboard provides that visibility by exposing the internal state machine behavior through a dedicated debugging interface.

The dashboard is exclusively for development and staging environments, providing insights that help debug issues like unexpected bot responses (e.g., "לא הבנתי" / "I didn't understand") by showing the pattern matching context and state machine transitions.

## Glossary

- **Dev_Dashboard**: The debugging web interface accessible at `/dev/dashboard` route that displays father state, messages, and workflow transitions
- **Father**: A parent user in the Dad Coach system who interacts via WhatsApp
- **Workflow_State**: The current position in the deterministic state machine (e.g., WELCOME, SCHEDULE_QUALITY_TIME, WAITING, QUALITY_TIME_FOLLOW_UP)
- **Message_Log**: Database table storing all inbound and outbound WhatsApp messages with timestamps
- **State_Transition_Log**: Database table (workflow_state_transition_log) recording all workflow state changes with trigger reasons
- **Message_Template**: Predefined response templates used by the workflow engine, stored in message_templates table
- **Belt**: Gamification level indicator (WHITE, YELLOW, ORANGE, GREEN, BLUE, BROWN, BLACK) representing father engagement
- **Pattern_Matching**: The process by which the workflow engine interprets incoming messages to determine appropriate responses
- **Trigger_Reason**: The categorized reason for a state transition (e.g., USER_MESSAGE, SCHEDULER, SYSTEM)
- **Dev_API**: Backend REST endpoints under `/api/v1/dev/` namespace providing debugging data
- **Israel_Timezone**: Asia/Jerusalem timezone (UTC+2/+3) used for displaying timestamps

## Requirements

### Requirement 1: Dev API - List All Fathers

**User Story:** As a developer, I want to retrieve a list of all fathers with their basic debugging info, so that I can select which father's conversation flow to inspect.

#### Acceptance Criteria

1. WHEN a GET request is sent to `/api/v1/dev/fathers`, THE Dev_API SHALL return a paginated list of fathers with id, display_name, phone, status, current_workflow_state, previous_workflow_state, current_belt, and last_interaction_at
2. WHEN the `search` query parameter is provided, THE Dev_API SHALL filter fathers by phone number or display_name containing the search string (case-insensitive)
3. WHEN the `page` and `page_size` query parameters are provided, THE Dev_API SHALL return the corresponding page of results with default page_size of 20, strictly enforced maximum of 100, and reject requests exceeding the maximum with HTTP 400 Bad Request
4. IF the environment is production, THEN THE Dev_API SHALL return HTTP 403 Forbidden with message "Dev endpoints disabled in production", and HTTP 403 SHALL take precedence over HTTP 404 when both conditions apply
5. THE Dev_API SHALL order results by last_interaction_at descending (most recent first) by default
6. WHEN a database query partially fails, THE Dev_API SHALL return HTTP 200 with the successfully retrieved partial data and include an error indicator for the failed portion

### Requirement 2: Dev API - Get Father State Details

**User Story:** As a developer, I want to retrieve detailed state information for a specific father, so that I can understand their current position in the workflow.

#### Acceptance Criteria

1. WHEN a GET request is sent to `/api/v1/dev/fathers/{id}/state`, THE Dev_API SHALL return the father's complete workflow state including current_workflow_state, previous_workflow_state, workflow_state_entered_at, current_belt, current_streak_weeks, total_quality_times_completed, and welcomed_at
2. WHEN the father ID exists, THE Dev_API SHALL also include the father's children list with id, name, and birth_date
3. WHEN the father ID exists, THE Dev_API SHALL also include any scheduled quality_time entries with child_name, scheduled_start, scheduled_end, and status
4. IF the environment is production, THEN THE Dev_API SHALL return HTTP 403 Forbidden, and HTTP 403 SHALL take precedence over HTTP 404 when both conditions apply
5. IF the father ID does not exist and environment is not production, THEN THE Dev_API SHALL return HTTP 404 Not Found
6. WHEN a database query partially fails, THE Dev_API SHALL return HTTP 200 with the successfully retrieved partial data and include an error indicator for the failed portion

### Requirement 3: Dev API - Get Message Log

**User Story:** As a developer, I want to retrieve the conversation history for a father, so that I can see what messages were exchanged and when.

#### Acceptance Criteria

1. WHEN a GET request is sent to `/api/v1/dev/fathers/{id}/messages`, THE Dev_API SHALL return the message_log entries for that father with id, direction, content, and created_at
2. WHEN the `limit` query parameter is provided, THE Dev_API SHALL return at most that many messages, with strictly enforced default of 50 and maximum of 200, and reject requests exceeding the maximum with HTTP 400 Bad Request
3. THE Dev_API SHALL order messages by created_at descending (newest first)
4. WHEN the `since` query parameter is provided with an ISO 8601 timestamp, THE Dev_API SHALL return only messages created after that timestamp
5. IF the environment is production, THEN THE Dev_API SHALL return HTTP 403 Forbidden, and HTTP 403 SHALL take precedence over HTTP 404 when both conditions apply
6. IF the father ID does not exist and environment is not production, THEN THE Dev_API SHALL return HTTP 404 Not Found

### Requirement 4: Dev API - Get State Transitions

**User Story:** As a developer, I want to retrieve the workflow state transition history for a father, so that I can trace how they moved through the state machine.

#### Acceptance Criteria

1. WHEN a GET request is sent to `/api/v1/dev/fathers/{id}/transitions`, THE Dev_API SHALL return the workflow_state_transition_log entries with id, from_state, to_state, trigger_reason, trigger_message_id, and created_at
2. WHEN the `limit` query parameter is provided, THE Dev_API SHALL return at most that many transitions, with strictly enforced default of 30 and maximum of 100, and reject requests exceeding the maximum with HTTP 400 Bad Request
3. THE Dev_API SHALL order transitions by created_at descending (newest first)
4. IF the environment is production, THEN THE Dev_API SHALL return HTTP 403 Forbidden, and HTTP 403 SHALL take precedence over HTTP 404 when both conditions apply
5. IF the father ID does not exist and environment is not production, THEN THE Dev_API SHALL return HTTP 404 Not Found

### Requirement 5: Environment Protection

**User Story:** As a security-conscious developer, I want the Dev Dashboard API to be disabled in production, so that sensitive debugging information is never exposed to end users.

#### Acceptance Criteria

1. WHEN the Spring profile is `prod` or `production`, THE Dev_API SHALL reject all requests with HTTP 403 Forbidden and block all dev API access
2. WHEN the Spring profile is any non-production value (including but not limited to `dev`, `local`, `staging`, `test`, `qa`), THE Dev_API SHALL process requests normally
3. THE Dev_API SHALL log a warning message when a request is rejected due to production environment, and SHALL reject the request even if logging fails
4. THE Dev_API SHALL NOT include stack traces OR internal error details in production rejection responses, ensuring neither type of sensitive information is exposed

### Requirement 6: Frontend - Father Selection

**User Story:** As a developer, I want to search and select a father from the dashboard, so that I can focus on debugging a specific user's conversation flow.

#### Acceptance Criteria

1. WHEN the Dev_Dashboard page loads, THE Dev_Dashboard SHALL display a searchable dropdown or input field for selecting a father
2. WHEN the user types in the search field, THE Dev_Dashboard SHALL filter the father list by phone number or display_name after 300ms debounce
3. WHEN a father is selected, THE Dev_Dashboard SHALL load and display that father's state, messages, and transitions
4. THE Dev_Dashboard SHALL remember the last selected father ID in browser localStorage
5. WHEN the page loads with a stored father ID, THE Dev_Dashboard SHALL automatically load that father's data

### Requirement 7: Frontend - Father State Display

**User Story:** As a developer, I want to see the current workflow state and father details prominently displayed, so that I can quickly understand where they are in the conversation flow.

#### Acceptance Criteria

1. WHEN a father is selected, THE Dev_Dashboard SHALL display the current_workflow_state with a color-coded badge
2. WHEN a father is selected, THE Dev_Dashboard SHALL display the previous_workflow_state for context
3. THE Dev_Dashboard SHALL display the workflow_state_entered_at timestamp converted to Israel timezone (Asia/Jerusalem)
4. THE Dev_Dashboard SHALL display the father's current_belt with an appropriate belt color indicator
5. THE Dev_Dashboard SHALL display the father's phone number, display_name, and status
6. THE Dev_Dashboard SHALL display the list of children associated with the father, showing an empty list or placeholder when the father has no children
7. WHEN only some dashboard components load successfully, THE Dev_Dashboard SHALL display the partial data that was successfully retrieved and show error indicators for failed components

### Requirement 8: Frontend - Real-Time Message Log

**User Story:** As a developer, I want to see the conversation messages in real-time, so that I can observe the actual WhatsApp interaction as it happens.

#### Acceptance Criteria

1. WHEN a father is selected, THE Dev_Dashboard SHALL display the message_log as a chat-style conversation view
2. THE Dev_Dashboard SHALL visually distinguish INBOUND messages (from father) and OUTBOUND messages (from bot) using different colors or alignment
3. THE Dev_Dashboard SHALL display each message's created_at timestamp converted to Israel timezone in HH:mm:ss format
4. THE Dev_Dashboard SHALL poll for new messages every 2 seconds
5. WHEN new messages arrive, THE Dev_Dashboard SHALL prepend them to the list without full page refresh
6. THE Dev_Dashboard SHALL provide a button to manually refresh messages immediately

### Requirement 9: Frontend - State Transition Log

**User Story:** As a developer, I want to see the history of workflow state transitions, so that I can understand why the bot moved between states.

#### Acceptance Criteria

1. WHEN a father is selected, THE Dev_Dashboard SHALL display the workflow_state_transition_log as a timeline
2. THE Dev_Dashboard SHALL display each transition's from_state, to_state, and trigger_reason
3. THE Dev_Dashboard SHALL display each transition's created_at timestamp converted to Israel timezone
4. IF a transition has a trigger_message_id, THE Dev_Dashboard SHALL display it as a clickable reference
5. THE Dev_Dashboard SHALL poll for new transitions every 3 seconds
6. THE Dev_Dashboard SHALL visually indicate the transition direction with an arrow (from_state → to_state)

### Requirement 10: Frontend - Timezone Display

**User Story:** As a developer, I want all timestamps displayed in Israel timezone, so that I can correlate events with the father's local time.

#### Acceptance Criteria

1. THE Dev_Dashboard SHALL convert all UTC timestamps to Israel timezone (Asia/Jerusalem) before display
2. THE Dev_Dashboard SHALL display dates in DD/MM/YYYY format for consistency with Israeli date conventions
3. THE Dev_Dashboard SHALL display times in HH:mm:ss 24-hour format
4. THE Dev_Dashboard SHALL include a visual indicator showing the current timezone being used
5. IF the timezone indicator fails to load, THEN THE Dev_Dashboard SHALL prevent timestamp display and show an error message indicating timezone information is unavailable

### Requirement 11: Frontend - Auto-Refresh Control

**User Story:** As a developer, I want to control the auto-refresh behavior, so that I can pause updates when analyzing specific data or resume real-time monitoring.

#### Acceptance Criteria

1. THE Dev_Dashboard SHALL provide a toggle to enable/disable auto-refresh polling
2. WHEN auto-refresh is enabled, THE Dev_Dashboard SHALL show a visual indicator (e.g., spinning icon or "Live" badge)
3. WHEN auto-refresh is disabled, THE Dev_Dashboard SHALL stop initiating new polling requests but allow in-flight requests to complete
4. THE Dev_Dashboard SHALL default to auto-refresh enabled on every page load, regardless of previous session state
5. THE Dev_Dashboard SHALL display the last refresh timestamp for each data section

### Requirement 12: Dev API - Environment Detection

**User Story:** As a backend developer, I want a centralized environment detection mechanism, so that all dev endpoints use consistent environment checking logic.

#### Acceptance Criteria

1. THE Dev_API SHALL use a DevEnvironmentGuard component to determine if dev endpoints are allowed
2. WHEN the `dadcoach.dev.enabled` property is explicitly set to true, THE Dev_API SHALL allow access regardless of Spring profile
3. WHEN the `dadcoach.dev.enabled` property is explicitly set to false, THE Dev_API SHALL block access regardless of Spring profile
4. WHEN the `dadcoach.dev.enabled` property is not set, THE Dev_API SHALL allow access only if Spring profile is not `prod` or `production`
5. IF the DevEnvironmentGuard component fails to determine the environment, THEN THE Dev_API SHALL block access and treat the failure as a security precaution

### Requirement 13: Parser - API Response Serialization

**User Story:** As a developer, I want API responses to be consistently serialized to JSON, so that the frontend can reliably parse the debugging data.

#### Acceptance Criteria

1. THE Dev_API SHALL serialize all timestamp fields to ISO 8601 format with timezone offset
2. THE Dev_API SHALL serialize enum fields (workflow_state, belt, status) as uppercase strings
3. THE Dev_API SHALL include a `_links` object with pagination URLs when returning paginated lists
4. FOR ALL valid response objects, parsing the JSON response then re-serializing SHALL produce equivalent JSON (round-trip property)

### Requirement 14: Frontend - Route Configuration

**User Story:** As a developer, I want the Dev Dashboard accessible at a dedicated route, so that it's easy to navigate to during debugging sessions.

#### Acceptance Criteria

1. THE Dev_Dashboard SHALL be accessible at the `/dev/dashboard` route in the Next.js application
2. WHEN navigating to `/dev`, THE Dev_Dashboard SHALL redirect to `/dev/dashboard`
3. THE Dev_Dashboard SHALL not be linked from the main application navigation
4. THE Dev_Dashboard SHALL display a clear "Development Only" warning banner at the top
