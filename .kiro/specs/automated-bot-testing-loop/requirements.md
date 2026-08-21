# Requirements Document

## Introduction

This feature creates an automated testing and continuous improvement loop for the Dad Coach WhatsApp bot. The system automatically tests the bot's conversation quality after each deployment by: resetting a test user, completing registration, simulating real WhatsApp conversations, evaluating conversation quality using AI, identifying issues, and triggering code changes through Kiro when problems are found. This creates a hands-free improvement cycle that continuously enhances bot conversion and user experience.

## Glossary

- **Test_Orchestrator**: The main automation script that coordinates the entire testing and improvement loop cycle
- **Test_User**: A designated user account (Oren Gaifman, phone 0503020551) used exclusively for automated testing
- **AI_Evaluator**: An AI component that analyzes conversation transcripts and determines if the bot responded appropriately
- **Kiro_Interface**: The interface through which improvement suggestions are communicated to Kiro for implementation
- **Conversation_Simulator**: A component that interacts with the WhatsApp bot as if it were a real user
- **Deploy_Monitor**: A component that detects when a new deployment has completed successfully
- **Issue_Report**: A structured document describing problems found during conversation evaluation with specific improvement recommendations
- **Quality_Score**: A numeric rating (1-10) indicating how well the bot performed in a conversation
- **Backend_API**: The Dad Coach Spring Boot API that manages users, invitations, and WhatsApp webhooks
- **Test_Conversation_Scenarios**: Predefined conversation flows that represent typical user interactions to test

## Requirements

### Requirement 1: Deploy Detection and Test Triggering

**User Story:** As a developer, I want the testing loop to automatically start after each deployment, so that every code change is validated without manual intervention.

#### Acceptance Criteria

1. WHEN a new deployment to the main branch completes successfully, THE Deploy_Monitor SHALL trigger a new test cycle within 60 seconds
2. THE Deploy_Monitor SHALL verify the Backend_API health endpoint returns status "UP" before starting the test cycle
3. IF the Backend_API health check fails after deployment, THEN THE Deploy_Monitor SHALL retry up to 5 times with 30-second intervals before marking the deployment as unhealthy
4. THE Test_Orchestrator SHALL log the start time, deployment commit SHA, and environment for each test cycle
5. WHILE a test cycle is in progress, THE Test_Orchestrator SHALL prevent additional test cycles from starting

### Requirement 2: Test User Reset

**User Story:** As a tester, I want the test user to be completely reset before each test, so that each test starts from a clean slate.

#### Acceptance Criteria

1. WHEN a test cycle begins, THE Test_Orchestrator SHALL delete the Test_User from the system if they exist
2. THE Test_Orchestrator SHALL call the Backend_API admin endpoint to delete the user with phone number 0503020551
3. THE Test_Orchestrator SHALL verify the Test_User no longer exists in the system before proceeding
4. IF the Test_User deletion fails, THEN THE Test_Orchestrator SHALL log the error and retry up to 3 times
5. THE Test_Orchestrator SHALL clear any cached data associated with the Test_User phone number

### Requirement 3: Invitation and Registration

**User Story:** As a tester, I want to automatically complete the registration process for the test user, so that the bot can be tested with a freshly registered user.

#### Acceptance Criteria

1. WHEN the Test_User has been successfully deleted, THE Test_Orchestrator SHALL create a new invitation link via the Backend_API
2. THE Test_Orchestrator SHALL complete the onboarding flow with predefined test data for the Test_User
3. THE Test_Orchestrator SHALL use the following test data: name "Oren Gaifman", phone "0503020551", language "Hebrew", timezone "Asia/Jerusalem"
4. THE Test_Orchestrator SHALL add one test child with name "Test Child", birth date "2020-01-15", gender "male"
5. THE Test_Orchestrator SHALL complete all onboarding steps: language, father profile, children, goals, preferences, and final activation
6. IF any onboarding step fails, THEN THE Test_Orchestrator SHALL log the specific step that failed with the error details
7. THE Test_Orchestrator SHALL verify the Test_User is in "ACTIVE" status after registration completion

### Requirement 4: WhatsApp Conversation Simulation

**User Story:** As a tester, I want to simulate realistic WhatsApp conversations with the bot, so that the bot's responses can be evaluated under realistic conditions.

#### Acceptance Criteria

1. WHEN registration is complete, THE Conversation_Simulator SHALL send the activation message "🚀 התחל" to initiate the conversation
2. THE Conversation_Simulator SHALL wait for the bot's response before sending the next message
3. THE Conversation_Simulator SHALL execute Test_Conversation_Scenarios that cover: initial greeting, child information sharing, emotional scenarios, and coaching requests
4. THE Conversation_Simulator SHALL record all messages (both sent and received) with timestamps for evaluation
5. THE Conversation_Simulator SHALL timeout after 30 seconds if no response is received from the bot
6. IF the bot fails to respond within the timeout, THEN THE Conversation_Simulator SHALL mark the conversation as failed and include this in the evaluation
7. THE Conversation_Simulator SHALL simulate at least 5 conversation exchanges before completing the test
8. THE Conversation_Simulator SHALL simulate realistic typing delays of 1-3 seconds between messages

### Requirement 5: AI Conversation Evaluation

**User Story:** As a developer, I want AI to evaluate the conversation quality, so that issues can be identified automatically without human review.

#### Acceptance Criteria

1. WHEN a test conversation completes, THE AI_Evaluator SHALL analyze the full conversation transcript
2. THE AI_Evaluator SHALL assign a Quality_Score between 1 and 10 for the overall conversation
3. THE AI_Evaluator SHALL evaluate conversations against the following criteria: response relevance, emotional intelligence, coaching quality, language appropriateness, and conversation flow
4. THE AI_Evaluator SHALL identify specific messages where the bot's response was inadequate
5. IF the Quality_Score is below 7, THEN THE AI_Evaluator SHALL generate an Issue_Report with specific improvement recommendations
6. THE AI_Evaluator SHALL compare the current conversation quality against previous test runs to detect regressions
7. THE AI_Evaluator SHALL flag any bot responses that seem generic, off-topic, or emotionally inappropriate
8. THE Issue_Report SHALL include: the problematic message exchange, the expected behavior, the actual behavior, and a suggested fix

### Requirement 6: Kiro Integration for Automated Fixes

**User Story:** As a developer, I want identified issues to be automatically communicated to Kiro, so that fixes can be implemented without manual intervention.

#### Acceptance Criteria

1. WHEN an Issue_Report is generated with Quality_Score below 7, THE Kiro_Interface SHALL create a structured improvement request
2. THE Kiro_Interface SHALL format the Issue_Report in a way that Kiro can understand and act upon
3. THE Kiro_Interface SHALL include the relevant code files that likely need modification based on the issue type
4. THE Kiro_Interface SHALL specify the expected behavior change in clear, testable terms
5. IF multiple issues are identified, THEN THE Kiro_Interface SHALL prioritize them by severity and address the most critical first
6. THE Kiro_Interface SHALL wait for Kiro to complete the fix and push to the main branch before continuing
7. WHEN Kiro pushes a fix to the main branch, THE Test_Orchestrator SHALL restart the entire test cycle to validate the fix

### Requirement 7: Loop Control and Safety

**User Story:** As a developer, I want safety controls to prevent infinite improvement loops, so that the system doesn't get stuck in an endless cycle.

#### Acceptance Criteria

1. THE Test_Orchestrator SHALL limit the number of consecutive improvement cycles to 5 per initial deployment
2. IF the Quality_Score does not improve after 3 consecutive fix attempts for the same issue, THEN THE Test_Orchestrator SHALL escalate to manual review
3. THE Test_Orchestrator SHALL send a notification when manual intervention is required
4. THE Test_Orchestrator SHALL maintain a log of all test cycles, Quality_Scores, and improvements made
5. THE Test_Orchestrator SHALL allow manual override to skip specific issues or continue beyond limits
6. WHILE the loop limit is reached, THE Test_Orchestrator SHALL pause and wait for manual approval before continuing
7. THE Test_Orchestrator SHALL generate a daily summary report of all test cycles and improvements

### Requirement 8: Test Scenario Management

**User Story:** As a developer, I want to define and manage test conversation scenarios, so that the bot is tested against representative user interactions.

#### Acceptance Criteria

1. THE Test_Orchestrator SHALL load Test_Conversation_Scenarios from a configuration file
2. THE Test_Conversation_Scenarios SHALL include scenario name, expected bot behavior, and user message sequence
3. THE Test_Orchestrator SHALL support adding new scenarios without code changes
4. THE Test_Orchestrator SHALL rotate through different scenarios across test cycles to ensure comprehensive coverage
5. THE Test_Conversation_Scenarios SHALL cover at least: happy path onboarding, emotional support requests, coaching advice requests, scheduling interactions, and edge cases (empty messages, very long messages, special characters)
6. WHEN a scenario consistently passes for 10 consecutive test cycles, THE Test_Orchestrator SHALL mark it as stable and reduce its test frequency

### Requirement 9: Reporting and Observability

**User Story:** As a developer, I want comprehensive reports on test results and improvements, so that I can track the bot's quality over time.

#### Acceptance Criteria

1. THE Test_Orchestrator SHALL generate a test report after each test cycle
2. THE Test_Report SHALL include: test cycle ID, timestamp, Quality_Score, issues found, improvements made, and pass/fail status
3. THE Test_Orchestrator SHALL store all test reports for historical analysis
4. THE Test_Orchestrator SHALL expose metrics via an API endpoint: average Quality_Score, total test cycles, improvement success rate, and common issue types
5. THE Test_Orchestrator SHALL send alerts when Quality_Score drops below 5 or when 3 consecutive tests fail
6. THE Test_Orchestrator SHALL provide a dashboard view of test history and quality trends
