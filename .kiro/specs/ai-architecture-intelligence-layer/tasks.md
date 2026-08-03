# Implementation Plan

## Overview

Implementation of the AI Architecture Intelligence Layer for Dad Coach — provider adapters, model routing, prompt assembly, safety classification, decision engine, mission planning, memory extraction, cost control, quality evaluation, telemetry, and database migrations.

## Task Dependency Graph

```json
{
  "waves": [
    {"tasks": ["1", "3"]},
    {"tasks": ["2", "13"]},
    {"tasks": ["4", "5", "10"]},
    {"tasks": ["6"]},
    {"tasks": ["7", "8", "9", "11", "12"]},
    {"tasks": ["14"]}
  ]
}
```

## Tasks

- [x] 1. AI Provider Interface & Adapters
  - [x] 1.1 Implement AiProvider interface defining sendPrompt(request) → response
  - [x] 1.2 Implement OpenAI adapter calling GPT-4o and GPT-4o-mini via WebClient
  - [x] 1.3 Implement Anthropic adapter calling Claude 3.5 via WebClient
  - [x] 1.4 Add Resilience4j circuit breaker per adapter (trips at 5% error over 1h)
  - [x] 1.5 Configure 10-second timeout per provider call
  - [x] 1.6 Create standardized AiProviderRequest/AiProviderResponse format hiding provider differences
  - [x] 1.7 Write integration test with WireMock validating API call format

- [x] 2. Model Router & Fallback Chain
  - [x] 2.1 Implement routing table mapping each ConversationType to (model, temperature, top_p, max_tokens)
  - [x] 2.2 Ensure same conversation type always maps to same model under normal conditions
  - [x] 2.3 Implement fallback order: same provider lower-tier → secondary provider → pre-written fallback
  - [x] 2.4 Ensure total fallback chain completes within 30 seconds
  - [x] 2.5 Ensure no step in the chain is skipped
  - [x] 2.6 Write pre-written fallbacks in father's preferred language (English or Hebrew)

- [x] 3. Prompt Templates & Registry
  - [x] 3.1 Implement templates loaded from YAML/resource files at startup
  - [x] 3.2 Implement version tracking per prompt type
  - [x] 3.3 Implement A/B test group assignment deterministic via hash(father_id) % 2
  - [x] 3.4 Ensure same father_id always gets same group (A or B)
  - [x] 3.5 Implement active version per prompt type retrieval
  - [x] 3.6 Support parameterized placeholders (Mustache-style) in templates

- [x] 4. Prompt Assembler & Token Budget
  - [x] 4.1 Implement total budget of 2000 tokens (system:400, memory:500, context:300, history:600, output:200)
  - [x] 4.2 Ensure each section never exceeds its allocated budget
  - [x] 4.3 Implement token counting using tiktoken4j (cl100k_base) for exact counts
  - [x] 4.4 Implement sliding window that always includes current user message and last assistant response
  - [x] 4.5 Implement history truncation removing oldest messages first when budget exceeded
  - [x] 4.6 Return assembled prompt as a list of AiMessage objects ready for provider

- [x] 5. Safety Classifier
  - [x] 5.1 Return exactly one classification with confidence in range 0.0 to 1.0
  - [x] 5.2 Ensure classification is never null or undefined
  - [x] 5.3 Detect crisis keywords in both English and Hebrew (self-harm, abuse, violence)
  - [x] 5.4 Return pre-written safety response with hotline numbers for CRISIS classification
  - [x] 5.5 Flag CHILD_SAFETY for human review with 2h SLA
  - [x] 5.6 Ensure classification runs BEFORE any coaching generation
  - [x] 5.7 Detect and block jailbreak/manipulation patterns

- [x] 6. Intelligence Layer Facade
  - [x] 6.1 Ensure all methods are stateless: receive context, return structured output
  - [x] 6.2 Implement generateCoachingResponse coordinating safety → prompt → route → validate
  - [x] 6.3 Implement generateMission returning MissionOutput record
  - [x] 6.4 Implement extractMemories returning MemoryExtractionOutput record
  - [x] 6.5 Implement classifyMessage delegating to SafetyClassifier
  - [x] 6.6 Implement decideDailyAction delegating to DecisionEngine
  - [x] 6.7 Ensure AI never directly mutates state — all outputs are recommendations

- [x] 7. Decision Engine
  - [x] 7.1 Ensure Priority 1 (SAFETY) always takes precedence regardless of other conditions
  - [x] 7.2 Ensure multiple matching priorities results in highest (lowest-numbered) winning
  - [x] 7.3 Ensure FOUNDATION phase never returns CHALLENGE
  - [x] 7.4 Ensure phase_day < 7 never returns REFLECT
  - [x] 7.5 Enforce 4-hour gap for proactive messages (returns WAIT if violated)
  - [x] 7.6 Track action history per father for gap enforcement

- [x] 8. Mission Planner
  - [x] 8.1 Ensure difficulty within phase bounds (never < 1)
  - [x] 8.2 Enforce category cooldown: same child 4 days, different child 2 days
  - [x] 8.3 Enforce child equity: |missions(childA) - missions(childB)| ≤ 1 over 7 days
  - [x] 8.4 Ensure violated equity forces next mission to target under-served child
  - [x] 8.5 Validate output against MissionOutput schema before return

- [x] 9. Memory Extractor
  - [x] 9.1 Extract memories from conversation transcript via AI
  - [x] 9.2 Include category, content, importance_score, confidence_score, subject_type in output
  - [x] 9.3 Limit content to 500 characters per memory
  - [x] 9.4 Ensure importance_score between 1-10 and confidence between 0.0-1.0
  - [x] 9.5 Return structured MemoryExtractionOutput (not raw AI text)
  - [x] 9.6 Never exceed token budget for extraction prompt

- [x] 10. Cost Controller & Budget Tracking
  - [x] 10.1 At 80% budget: non-critical calls use GPT-4o-mini
  - [x] 10.2 At 90%: memory injection reduced to 8
  - [x] 10.3 At 95%: cached/template responses only
  - [x] 10.4 At 100%: no AI calls at all
  - [x] 10.5 Ensure lower tiers don't apply restrictions from higher tiers
  - [x] 10.6 Track usage per father per day (resets at midnight UTC)
  - [x] 10.7 Make budget configurable via application.yml

- [x] 11. Quality Scorer & Evaluation
  - [x] 11.1 Implement quality formula: (mcr×0.3) + (nor×0.25) + (ccr×0.25) + (nsd×0.2)
  - [x] 11.2 Ensure result always in range 0 to 100
  - [x] 11.3 Normalize each component to 0-100 before formula
  - [x] 11.4 Implement A/B test group comparison
  - [x] 11.5 Persist quality score in telemetry record

- [x] 12. Output Validators & Schemas
  - [x] 12.1 Validate CoachingResponse, MissionOutput, MemoryExtractionOutput, SafetyClassification, ActionRecommendation, WeeklySummaryOutput, ReflectionInsightOutput
  - [x] 12.2 Check required fields for non-null
  - [x] 12.3 Validate enum values match allowed sets
  - [x] 12.4 Validate numeric values within specified ranges
  - [x] 12.5 Validate string lengths within specified bounds
  - [x] 12.6 Ensure confidence always in range 0.0 to 1.0
  - [x] 12.7 Return structured ValidationResult with failure details

- [x] 13. AI Telemetry Service
  - [x] 13.1 Log every AI call with request_id, father_id, model, tokens_in, tokens_out, cost, latency
  - [x] 13.2 Include validation_passed, fallback_used, retry_count, quality_score in records
  - [x] 13.3 Record A/B test group per call
  - [x] 13.4 Implement alert triggers: error_rate > 5% over 30min, latency p95 > 10s over 15min
  - [x] 13.5 Ensure telemetry write is async (does not block response delivery)

- [x] 14. Flyway Migration - AI Tables
  - [x] 14.1 Create ai_telemetry table with all columns from design
  - [x] 14.2 Create ai_daily_summary table with (father_id, date) primary key
  - [x] 14.3 Create prompt_versions table with unique(prompt_type, version)
  - [x] 14.4 Add indexes on father_id, model_name, created_at
  - [x] 14.5 Verify migration runs successfully against PostgreSQL

## Notes

- Dependencies: Task 2 depends on Task 1; Task 4 depends on Task 3; Task 5 depends on Task 2; Task 6 depends on Tasks 2, 4, 5; Tasks 7-9, 11-12 depend on Task 6; Task 10 depends on Task 2; Task 13 depends on Task 1; Task 14 depends on Tasks 3, 10, 13.
- Property-based tests using jqwik with minimum 100 iterations per property.
- Integration tests use WireMock for LLM provider mocking and Testcontainers for PostgreSQL.
