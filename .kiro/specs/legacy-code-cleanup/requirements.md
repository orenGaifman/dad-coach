# Requirements Document

## Introduction

This specification defines the cleanup of legacy code and database tables that were superseded by the deterministic workflow engine (SPEC: deterministic-workflow-engine). The Dad Coach application has been transformed from an AI-driven conversational coaching experience to a deterministic workflow engine. The new `WorkflowEngine` has replaced the old `ConversationOrchestrator`. 

Currently, a feature flag (`deterministicWorkflowEngine`) allows fallback to the old system, but the new system is stable and the legacy code should be removed to:
1. Reduce codebase complexity and maintenance burden
2. Eliminate confusion between old and new architectures
3. Improve application startup time by removing unused beans
4. Clean up database schema by dropping unused tables
5. Remove the technical debt of maintaining two parallel systems

**Scope**: This cleanup targets all components, services, entities, repositories, and database tables that were part of the legacy conversation-based orchestration system and are no longer used by the deterministic workflow engine.

## Glossary

- **Legacy_System**: The original AI-driven conversation orchestration architecture (ConversationOrchestrator, conversation package, and related tables) that has been replaced by the WorkflowEngine
- **WorkflowEngine**: The new deterministic state machine orchestrator that owns all business logic and state transitions
- **Feature_Flag**: The `deterministicWorkflowEngine` configuration flag that currently allows fallback to the legacy system
- **Flyway_Migration**: A database version-controlled migration script used to alter database schema
- **Dead_Code**: Code that is no longer executed or referenced by the active system
- **Orphan_Table**: A database table that is no longer read from or written to by the application

## Requirements

### Requirement 1: Remove Legacy Conversation Engine Tables

**User Story:** As a database administrator, I want obsolete database tables removed, so that the schema reflects only the active system and reduces storage overhead.

#### Acceptance Criteria

1. WHEN the Flyway migration is executed, THE Legacy_Cleanup_Migration SHALL drop the `conversations` table (created in V8__conversation_engine.sql)
2. WHEN the Flyway migration is executed, THE Legacy_Cleanup_Migration SHALL drop the `conversation_messages` table (created in V8__conversation_engine.sql)
3. WHEN the Flyway migration is executed, THE Legacy_Cleanup_Migration SHALL drop the `processed_messages` table (created in V8__conversation_engine.sql)
4. WHEN the Flyway migration is executed, THE Legacy_Cleanup_Migration SHALL drop the `side_effect_outbox` table (created in V8__conversation_engine.sql)
5. WHEN the Flyway migration is executed, THE Legacy_Cleanup_Migration SHALL drop the `growth_signals` table (created in V8.001__create_growth_signals.sql)
6. WHEN the Flyway migration is executed, THE Legacy_Cleanup_Migration SHALL drop the `ai_profiles` table (created in V16__create_ai_profiles.sql)
7. IF any table has existing data, THEN THE Legacy_Cleanup_Migration SHALL log the row count before dropping for audit purposes
8. THE Legacy_Cleanup_Migration SHALL use CASCADE to handle foreign key dependencies where necessary

### Requirement 2: Remove Legacy Domain Tables

**User Story:** As a database administrator, I want domain tables that are superseded by the new quality_time system removed, so that the schema is simplified.

#### Acceptance Criteria

1. WHEN the Flyway migration is executed, THE Legacy_Cleanup_Migration SHALL drop the `conversation` table (created in V3__domain_tables.sql, superseded by workflow_state_transition_log)
2. WHEN the Flyway migration is executed, THE Legacy_Cleanup_Migration SHALL drop the `coaching_session` table (created in V3__domain_tables.sql)
3. WHEN the Flyway migration is executed, THE Legacy_Cleanup_Migration SHALL drop the `reflection` table (created in V3__domain_tables.sql)
4. WHEN the Flyway migration is executed, THE Legacy_Cleanup_Migration SHALL drop the `weekly_summary` table (created in V3__domain_tables.sql, workflow engine has real-time metrics)
5. THE Legacy_Cleanup_Migration SHALL verify dependencies before dropping any table to avoid cascade issues with active tables

### Requirement 3: Preserve Active Flash Mission and Goal System

**User Story:** As a developer, I want to ensure the flash mission and goal system tables are NOT removed, so that active features continue to work.

#### Acceptance Criteria

1. THE Legacy_Cleanup_Migration SHALL NOT drop the `flash_mission_template` table (actively used by FlashMissionService)
2. THE Legacy_Cleanup_Migration SHALL NOT drop the `father_goal` table (actively used for weekly/monthly goals)
3. THE Legacy_Cleanup_Migration SHALL NOT modify any columns added to the `father` table for goals (weekly_goal_minutes, monthly_goal_minutes, etc.)

### Requirement 4: Remove Conversation Package Code

**User Story:** As a developer, I want all legacy conversation engine code removed, so that the codebase only contains active code paths.

#### Acceptance Criteria

1. THE Code_Cleanup SHALL delete the entire `com.dadcoach.conversation` package including all subpackages:
   - `conversation/ai/` (AiOrchestrator, FallbackResponseProvider, ResponseValidator)
   - `conversation/context/` (ContextAssembler, ConversationContext)
   - `conversation/dto/` (InboundMessageDto, OutboundMessageDto - legacy versions)
   - `conversation/entity/` (Conversation, ConversationMessage, ProcessedMessage, SideEffectOutbox)
   - `conversation/event/` (ConversationEventPublisher)
   - `conversation/memory/` (MemoryOrchestrator)
   - `conversation/mission/` (MissionOrchestrator)
   - `conversation/recovery/` (ConversationRecoveryService)
   - `conversation/repository/` (ConversationRepository, ConversationMessageRepository, etc.)
   - `conversation/sideeffect/` (SideEffectScheduler, SideEffectProcessor)
2. THE Code_Cleanup SHALL delete the main conversation classes:
   - ConversationOrchestrator.java
   - ConversationService.java / ConversationServiceImpl.java
   - ConversationProperties.java
   - IdempotencyService.java
   - SessionLockService.java
   - FatherResolver.java / FatherResolverImpl.java
   - MessageProcessor.java / MessageProcessorImpl.java
3. WHEN the conversation package is deleted, THE Application SHALL still compile without errors
4. WHEN the conversation package is deleted, THE Application SHALL still pass all existing tests

### Requirement 5: Remove Feature Flag Fallback Code

**User Story:** As a developer, I want the feature flag fallback mechanism removed from WhatsAppWebhookController, so that only the WorkflowEngine path exists.

#### Acceptance Criteria

1. THE Webhook_Cleanup SHALL remove the `ConversationOrchestrator` dependency from WhatsAppWebhookController
2. THE Webhook_Cleanup SHALL remove the feature flag check (`featureFlags.isDeterministicWorkflowEngine()`)
3. THE Webhook_Cleanup SHALL remove the `processWithConversationOrchestrator` method
4. THE Webhook_Cleanup SHALL modify the `handleWebhook` method to directly call `processWithWorkflowEngine` without any conditional
5. THE Webhook_Cleanup SHALL remove the import of ConversationOrchestrator and related DTOs
6. WHEN the cleanup is complete, THE WhatsAppWebhookController SHALL only use the WorkflowEngine for message processing

### Requirement 6: Update Feature Flags Configuration

**User Story:** As a developer, I want the `deterministicWorkflowEngine` feature flag removed, so that there is no ambiguity about which system is active.

#### Acceptance Criteria

1. THE Config_Cleanup SHALL remove the `deterministicWorkflowEngine` field from FeatureFlagsConfig
2. THE Config_Cleanup SHALL remove the getter and setter for `deterministicWorkflowEngine`
3. THE Config_Cleanup SHALL update the toString() method to exclude the removed flag
4. THE Config_Cleanup SHALL remove the `deterministic-workflow-engine` property from application.yml
5. THE FeatureFlagsConfig SHALL retain the `aiMessageGeneration` and `morningReminders` flags (still in use)

### Requirement 7: Remove Unused Mission Engine Package

**User Story:** As a developer, I want legacy mission orchestration code removed, so that only the new MissionService remains.

#### Acceptance Criteria

1. IF the `com.dadcoach.missionengine` package is not used by the WorkflowEngine, THEN THE Code_Cleanup SHALL delete the entire package
2. IF the `com.dadcoach.conversation.mission.MissionOrchestrator` is different from `com.dadcoach.mission.MissionService`, THEN THE Code_Cleanup SHALL delete only the conversation.mission package
3. THE Code_Cleanup SHALL preserve the `com.dadcoach.mission` package (contains the active MissionService)
4. THE Code_Cleanup SHALL preserve the `com.dadcoach.qualitytime` package (contains active QualityTime system)

### Requirement 8: Remove Legacy Memory System Components

**User Story:** As a developer, I want unused memory system code removed, so that only the simplified memory approach in the workflow engine remains.

#### Acceptance Criteria

1. IF the `com.dadcoach.memorysystem` package is not referenced by the WorkflowEngine, THEN THE Code_Cleanup SHALL delete it
2. IF the `com.dadcoach.memory` package is not referenced by the WorkflowEngine, THEN THE Code_Cleanup SHALL delete it
3. THE Code_Cleanup SHALL verify each memory-related component for active references before deletion
4. THE memory table from V3__domain_tables.sql SHALL be analyzed for active usage before deciding on removal

### Requirement 9: Clean Up Legacy Tests

**User Story:** As a developer, I want tests for deleted code removed, so that the test suite only validates active functionality.

#### Acceptance Criteria

1. THE Test_Cleanup SHALL delete all test files in `src/test/java/com/dadcoach/conversation/` and its subpackages
2. THE Test_Cleanup SHALL delete any integration tests that specifically test the ConversationOrchestrator
3. THE Test_Cleanup SHALL update any remaining tests that have imports or references to deleted code
4. WHEN test cleanup is complete, THE test suite SHALL run without compilation errors
5. WHEN test cleanup is complete, THE test suite SHALL have no skipped tests due to missing dependencies

### Requirement 10: Verify Application Integrity Post-Cleanup

**User Story:** As a quality assurance, I want verification that the cleanup did not break any active functionality, so that the production system remains stable.

#### Acceptance Criteria

1. WHEN all cleanup is complete, THE Application SHALL start successfully
2. WHEN all cleanup is complete, THE Application SHALL process WhatsApp messages through the WorkflowEngine
3. WHEN all cleanup is complete, THE Application SHALL complete all database migrations without errors
4. WHEN all cleanup is complete, THE Application SHALL pass all remaining unit and integration tests
5. THE Cleanup_Verification SHALL include a manual smoke test of the WhatsApp → WorkflowEngine → Response path

### Requirement 11: Document Removed Components

**User Story:** As a developer, I want a record of what was removed, so that future developers understand the architectural decision.

#### Acceptance Criteria

1. THE Documentation SHALL include a summary of all removed database tables with their original purpose
2. THE Documentation SHALL include a summary of all removed code packages with their original purpose
3. THE Documentation SHALL reference the deterministic-workflow-engine spec as the replacement architecture
4. THE Documentation SHALL be added to the docs/ folder or as comments in the Flyway migration
