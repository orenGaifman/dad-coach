# Technical Design — AI Architecture Intelligence Layer

## Architecture

### Overview

The Intelligence Layer implements all AI capabilities for Dad Coach: prompt assembly, model routing, response generation, safety classification, mission planning, memory extraction, and quality evaluation. It operates as a stateless advisory subsystem — receiving context and returning structured recommendations per SPEC-003 Requirement 14.

Built within the Spring Boot monolith using Spring's `WebClient` for async AI provider calls, with a provider-agnostic adapter pattern for multi-model support.

### Architecture Decisions

**AD-1: Provider Adapter Pattern** — Each AI provider (OpenAI, Anthropic, Google) is implemented as an adapter behind a common `AiProvider` interface. The Model_Router selects the provider at runtime based on conversation type and cost rules.

**AD-2: Stateless Request/Response** — Every AI function receives all required context as input and returns a structured output. No hidden state, no session affinity, no in-memory caches of father data.

**AD-3: Prompt Templates as Resources** — Prompt templates are stored as versioned resource files (YAML/Mustache) loaded at startup and cacheable. The Prompt_Registry tracks versions and A/B test assignments.

**AD-4: Token Counting with tiktoken4j** — Token budget enforcement uses tiktoken4j (Java port of OpenAI's tiktoken) for exact cl100k_base token counting before prompt assembly.

**AD-5: Structured Output via JSON Mode** — All AI calls that require structured output (missions, memory extraction, safety classification) use the provider's JSON mode with schema enforcement. Responses are deserialized and validated against Java record schemas.

**AD-6: Circuit Breaker per Provider** — Resilience4j circuit breakers protect each provider connection. When a provider trips, the Fallback_Chain routes to the next provider automatically.

### Package Structure

```
com.dadcoach.ai/
├── IntelligenceLayer.java             # Public interface (typed contracts from Req 14)
├── IntelligenceLayerImpl.java         # Implementation coordinating sub-components
├── prompt/
│   ├── PromptAssembler.java           # Composes prompts from sections (Req 3)
│   ├── PromptRegistry.java            # Version tracking, A/B test assignment
│   ├── PromptTemplate.java            # Value object for parameterized templates
│   ├── TokenBudgetManager.java        # Section-level token allocation (Req 5)
│   └── templates/                     # YAML template files (versioned)
├── routing/
│   ├── ModelRouter.java               # Selects provider+model per request type (Req 10)
│   ├── FallbackChain.java             # Ordered provider fallback (Req 10 criteria 3)
│   └── CostController.java            # Budget tracking and cost-reduction rules (Req 11)
├── provider/
│   ├── AiProvider.java                # Interface: sendPrompt(messages, params) → response
│   ├── AiProviderRequest.java         # Standardized internal request format
│   ├── AiProviderResponse.java        # Standardized internal response format
│   ├── openai/
│   │   └── OpenAiProvider.java        # OpenAI adapter (GPT-4o, GPT-4o-mini)
│   └── anthropic/
│       └── AnthropicProvider.java     # Anthropic adapter (Claude 3.5)
├── safety/
│   ├── SafetyClassifier.java          # Inbound message classification (Req 9)
│   └── SafetyKeywords.java            # English and Hebrew keyword lists for detection
├── decision/
│   ├── DecisionEngine.java            # Priority-tree action selection (Req 4)
│   └── ActionHistory.java             # Per-father action tracking
├── mission/
│   └── MissionPlanner.java            # Mission generation + validation (Req 7)
├── extraction/
│   └── MemoryExtractor.java           # Conversation → memory recommendations (Req 15)
├── evaluation/
│   ├── QualityScorer.java             # Automated response quality check (Req 12)
│   └── EvaluationEngine.java          # Metrics correlation, A/B analysis
├── output/
│   ├── CoachingResponse.java          # Java record (Req 15 schema)
│   ├── MissionOutput.java             # Java record
│   ├── MemoryExtractionOutput.java    # Java record
│   ├── SafetyClassification.java      # Java record
│   ├── ActionRecommendation.java      # Java record
│   └── OutputValidator.java           # Schema validation per output type
└── telemetry/
    ├── AiTelemetryService.java        # Structured telemetry emission (Req 16)
    └── AiTelemetryRecord.java         # Record schema
```

## Components and Interfaces

### IntelligenceLayer Interface (Public Contract)

```java
public interface IntelligenceLayer {
    CoachingResponse generateCoachingResponse(CoachingContext context);
    MissionOutput generateMission(MissionContext context);
    MemoryExtractionOutput extractMemories(CompletedConversation conversation);
    SafetyClassification classifyMessage(InboundMessage message);
    ActionRecommendation decideDailyAction(DailyDecisionContext context);
    WeeklySummaryOutput generateSummary(SummaryPeriod period);
    ReflectionInsightOutput evaluateReflection(ReflectionInput input);
}
```

### ModelRouter

```java
@Service
public class ModelRouter {
    // Routing table from SPEC-003 Req 10 criteria 1
    private static final Map<ConversationType, ModelConfig> ROUTING = Map.of(
        ONBOARDING, new ModelConfig("gpt-4o", 0.7, 0.9, 300),
        DIFFICULT_SITUATION, new ModelConfig("gpt-4o", 0.7, 0.9, 400),
        DAILY_COACHING, new ModelConfig("gpt-4o-mini", 0.8, 0.95, 300),
        MISSION_GENERATION, new ModelConfig("gpt-4o-mini", 0.3, 0.8, 400)
        // ... full table
    );

    public AiProviderResponse route(AiProviderRequest request, ConversationType type) {
        ModelConfig config = ROUTING.get(type);
        return fallbackChain.execute(request, config);
    }
}
```

### PromptAssembler

```java
@Service
public class PromptAssembler {
    // Budget from SPEC-003 Req 3 criteria 2: 2000 tokens total
    // System+Persona: 400, Memories: 500, Context: 300, History: 600, Output: 200
    
    public List<AiMessage> assemble(CoachingContext context) {
        var budget = new TokenBudgetManager(2000);
        var messages = new ArrayList<AiMessage>();
        
        messages.add(buildSystemPrompt(context, budget));      // 400 tokens
        messages.add(buildMemoryBlock(context, budget));        // 500 tokens
        messages.add(buildContextBlock(context, budget));       // 300 tokens
        messages.addAll(buildHistory(context, budget));         // 600 tokens
        messages.add(buildOutputInstructions(context, budget)); // 200 tokens
        
        return messages;
    }
}
```

## Data Models

### AI Telemetry Table

```sql
CREATE TABLE ai_telemetry (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id          UUID NOT NULL,
    father_id           UUID NOT NULL,
    conversation_id     UUID,
    conversation_type   VARCHAR(30),
    interaction_type    VARCHAR(30) NOT NULL,
    prompt_version      VARCHAR(20),
    model_provider      VARCHAR(20) NOT NULL,
    model_name          VARCHAR(50) NOT NULL,
    temperature         REAL,
    input_tokens        INTEGER NOT NULL,
    output_tokens       INTEGER NOT NULL,
    estimated_cost_usd  REAL,
    total_latency_ms    INTEGER NOT NULL,
    llm_latency_ms      INTEGER,
    validation_passed   BOOLEAN NOT NULL,
    fallback_used       BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count         INTEGER NOT NULL DEFAULT 0,
    quality_score       REAL,
    safety_classification VARCHAR(30),
    ab_test_group       VARCHAR(5),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_telemetry_father ON ai_telemetry(father_id, created_at DESC);
CREATE INDEX idx_ai_telemetry_model ON ai_telemetry(model_name, created_at DESC);

-- Daily summary (materialized, refreshed by scheduler)
CREATE TABLE ai_daily_summary (
    father_id           UUID NOT NULL,
    date                DATE NOT NULL,
    total_calls         INTEGER NOT NULL DEFAULT 0,
    total_input_tokens  INTEGER NOT NULL DEFAULT 0,
    total_output_tokens INTEGER NOT NULL DEFAULT 0,
    total_cost_usd      REAL NOT NULL DEFAULT 0,
    fallback_count      INTEGER NOT NULL DEFAULT 0,
    average_quality     REAL,
    PRIMARY KEY (father_id, date)
);
```

### Prompt Versions Table

```sql
CREATE TABLE prompt_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_type     VARCHAR(30) NOT NULL,
    version         VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT FALSE,
    ab_test_group   VARCHAR(5),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(prompt_type, version)
);
```

## Error Handling

| Scenario | Handling |
|----------|----------|
| Primary provider timeout (10s) | Fall to next in Fallback_Chain |
| All providers fail | Return pre-written Fallback_Response; schedule deferred retry |
| Validation failure (bad schema) | Retry once with correction instruction; then fallback |
| Rate limit from provider | Circuit breaker opens; route to secondary for 30 min |
| Daily per-father budget exceeded | Switch to cheapest model; at 100% → fallback only |
| Safety classification: CRISIS | Return pre-written safety response; log for human review |

## Correctness Properties

- AI NEVER directly mutates state — all outputs are recommendations validated by the application layer
- Every AI call is metered: input_tokens, output_tokens, cost, latency recorded in telemetry
- Safety classification runs BEFORE any coaching generation — unsafe messages never reach the AI model for coaching
- Token budgets are computed EXACTLY (tiktoken) not estimated — prompts never exceed model context windows
- Fallback_Responses are static text, never AI-generated — guaranteed safe content
- A/B test group assignment is deterministic per father_id (hash-based) — consistent experience

## Requirement Traceability

| Requirement | Design Element |
|-------------|---------------|
| Req 1-2: Philosophy + Persona | Encoded in prompt templates (system + persona sections) |
| Req 3: Prompt Architecture | `PromptAssembler` + `TokenBudgetManager` + template files |
| Req 4: Decision Engine | `DecisionEngine` — priority tree implementation |
| Req 5-6: Context + Memory Injection | `PromptAssembler` memory/context sections |
| Req 7: Mission Planning | `MissionPlanner` — generation + validation |
| Req 8: Prompt Versioning | `PromptRegistry` + `prompt_versions` table |
| Req 9: Safety Layer | `SafetyClassifier` — keyword + semantic classification |
| Req 10: Multi-Model | `ModelRouter` + `FallbackChain` + provider adapters |
| Req 11: Cost Optimization | `CostController` + `ai_daily_summary` budget tracking |
| Req 12: Evaluation | `QualityScorer` + `EvaluationEngine` |
| Req 13: Future Capabilities | Feature flags in config; adapter interfaces extensible |
| Req 14: Decision Boundaries | `IntelligenceLayer` interface — stateless, advisory only |
| Req 15: Output Contracts | Java records + `OutputValidator` |
| Req 16: Observability | `AiTelemetryService` + `ai_telemetry` table |

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Token Budget Invariant

*For any* conversation type and any coaching context, the assembled prompt's total token count SHALL NOT exceed 2000 tokens, and each section (system: ≤400, memory: ≤500, context: ≤300, history: ≤600, output instructions: ≤200) SHALL NOT exceed its allocated budget.

**Validates: Requirements 3.2, 5.1**

### Property 2: Sliding Window Minimum Guarantee

*For any* conversation history (regardless of length), after the sliding window is applied within the 600-token budget, the result SHALL always contain at minimum the user's current message and the last assistant response.

**Validates: Requirements 3.7, 5.2**

### Property 3: Decision Engine Priority Ordering

*For any* DailyDecisionContext where multiple priority levels match, the Decision Engine SHALL always return the action corresponding to the highest (lowest-numbered) matching priority level. If priority 1 (SAFETY) conditions are met, SAFETY_RESPONSE is always returned regardless of other conditions.

**Validates: Requirements 4.1**

### Property 4: Decision Engine Phase Constraints

*For any* DailyDecisionContext where the father is in FOUNDATION phase, the Decision Engine SHALL never return CHALLENGE. For any context where phase_day < 7, it SHALL never return REFLECT.

**Validates: Requirements 4.8**

### Property 5: Proactive Message Gap Enforcement

*For any* action history where a proactive outbound message was sent less than 4 hours ago, the Decision Engine SHALL return WAIT for any proactive action (non-response-to-inbound actions).

**Validates: Requirements 4.5**

### Property 6: Memory Composite Score Ordering

*For any* set of memories with computed composite scores using `(importance × 0.5) + (recency_factor × 0.3) + (relevance × 0.2)`, the selected memories SHALL be ordered by descending composite score, and the top-scoring memory SHALL always be included if the token budget allows at least one memory.

**Validates: Requirements 6.1, 6.2**

### Property 7: Memory Diversity Enforcement

*For any* memory selection result, no single memory category SHALL appear more than 5 times. If more than 5 memories of a single category would otherwise be selected (by score), the lowest-scoring memories of that category SHALL be replaced by the next-highest-scoring memories from other categories.

**Validates: Requirements 6.3**

### Property 8: Memory Confidence Floor

*For any* memory with confidence_score < 0.3, that memory SHALL never appear in the injected memory set, regardless of its importance_score or relevance_to_topic score.

**Validates: Requirements 6.10**

### Property 9: Mission Difficulty Bounds

*For any* coaching phase and mission history, the calculated difficulty level SHALL be >= phase minimum and <= phase maximum. The difficulty SHALL never be less than 1 regardless of adjustment calculations.

**Validates: Requirements 7.2**

### Property 10: Mission Category Cooldown Enforcement

*For any* mission planning request, if a category was used for the same child within the last 4 days, that category SHALL be excluded from selection. If a category was used for a different child within the last 2 days, that category SHALL be excluded from selection.

**Validates: Requirements 7.3**

### Property 11: Mission Child Equity Distribution

*For any* father with multiple children, over any 7-day window, the absolute difference in mission counts between any two children SHALL be <= 1. If the constraint is violated, the next mission MUST target the child with fewer missions.

**Validates: Requirements 7.4**

### Property 12: Cost Controller Tier Enforcement

*For any* father's daily token consumption level, the correct cost-reduction tier SHALL be enforced: at 80% → all non-critical calls use GPT-4o-mini; at 90% → memory injection reduced to 8; at 95% → cached/template responses only; at 100% → no AI calls. Lower tiers SHALL NOT apply restrictions from higher tiers.

**Validates: Requirements 11.1, 11.6**

### Property 13: Model Routing Determinism

*For any* conversation type, when no cost or error constraints are active, the Model Router SHALL always return the model specified in the routing table for that type. The same conversation type SHALL always map to the same model configuration under identical conditions.

**Validates: Requirements 10.1**

### Property 14: Fallback Chain Ordering

*For any* failed primary model request, the system SHALL attempt fallback in strict order: (1) same provider lower-tier model, (2) secondary provider, (3) pre-written fallback. No step SHALL be skipped, and the total chain SHALL complete within 30 seconds.

**Validates: Requirements 10.3**

### Property 15: Output Schema Validation

*For any* AI output of type CoachingResponse, MissionOutput, MemoryExtractionOutput, SafetyClassification, ActionRecommendation, WeeklySummaryOutput, or ReflectionInsightOutput, all required fields SHALL be present and non-null, enum values SHALL match allowed sets, numeric values SHALL be within specified ranges, string lengths SHALL be within specified bounds, and confidence SHALL be in [0.0, 1.0].

**Validates: Requirements 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7, 15.8**

### Property 16: Safety Classification Completeness

*For any* inbound message, the Safety Layer SHALL return exactly one classification from the defined set (SAFE, EMOTIONAL_DISTRESS, CRISIS, CHILD_SAFETY, MEDICAL, LEGAL, MANIPULATION, OFF_TOPIC) with a confidence score in [0.0, 1.0]. The classification SHALL never be null or undefined.

**Validates: Requirements 9.1**

### Property 17: Quality Score Formula Correctness

*For any* set of quality signals (mission_completion_rate, normalized_outcome_rating, conversation_continuation_rate, normalized_streak_days), the composite AI Quality Score SHALL equal `(mcr × 0.3) + (nor × 0.25) + (ccr × 0.25) + (nsd × 0.2)` and SHALL always be in the range [0, 100].

**Validates: Requirements 12.2**

### Property 18: A/B Test Group Assignment Determinism

*For any* father_id, the A/B test group assignment SHALL be deterministic: `hash(father_id) % 2` always produces the same group ("A" or "B") for the same father_id. Two calls with the same father_id SHALL never return different groups.

**Validates: Requirements 8.3**

### Property 19: Alert Threshold Detection

*For any* metric time series, when a metric exceeds its defined threshold for the specified window duration (e.g., error_rate > 5% over 30 minutes, latency p95 > 10s over 15 minutes), an alert SHALL be generated. When the metric is below threshold, no alert SHALL fire.

**Validates: Requirements 16.4**

---

## Error Handling

### Error Categories and Recovery Strategies

| Error Type | Detection | Recovery | User Impact |
|---|---|---|---|
| PROVIDER_TIMEOUT | LLM call exceeds 10s | Fallback chain (next model) | Slightly delayed response |
| PROVIDER_ERROR | Non-2xx response from LLM API | Retry once, then fallback chain | None (transparent retry) |
| RATE_LIMIT | 429 from LLM provider | Switch to secondary provider immediately | None |
| VALIDATION_FAILURE | Output doesn't match schema | Retry with correction prompt; if fails, use pre-written fallback | Slightly generic response |
| CONTEXT_ASSEMBLY_ERROR | Token counting or memory lookup fails | Assemble with minimal context (system prompt + current message only) | Less personalized response |
| SAFETY_BLOCK | Safety Layer blocks output delivery | Use pre-written safety response | Receives appropriate safety resource |
| BUDGET_EXCEEDED | Daily token/call limit reached | Use cached or pre-written responses | Responses are templates until next day |
| DATABASE_READ_FAILURE | Cannot read memories/context | Proceed with empty context sections | Less personalized response |

### Fallback Response Strategy

Every conversation type has a pre-written fallback message library:

```java
public enum FallbackType {
    RETRY_SAME_MODEL,       // Same model, appended correction instruction
    FALLBACK_MODEL,         // Lower-tier model on same provider  
    SECONDARY_PROVIDER,     // Different provider entirely
    CACHED_RESPONSE,        // Previously cached response matching context
    PREWRITTEN_TEMPLATE     // Static template, last resort
}
```

**Fallback message requirements:**
- Written in father's preferred language (English or Hebrew, matching their language_preferences setting)
- Generic enough to work without specific context
- Still warm and on-brand with the coaching persona
- Includes a re-engagement hook (question or light prompt)
- Maximum 3 consecutive fallback responses before alerting operations

### Circuit Breaker Pattern

The Model Router implements a circuit breaker per provider:
- **Closed** (normal): requests flow through
- **Open** (tripped): error_rate > 5% over 1 hour → all requests routed to fallback for 30 minutes
- **Half-open** (testing): after 30 minutes, send 10% of traffic to test recovery; if successful, close circuit

### Graceful Degradation Levels

```
Level 0 (Normal):     Full context, primary model, all features active
Level 1 (Stressed):   Full context, switch non-critical to mini model (80% budget)
Level 2 (Degraded):   Reduced memories (8), mini model only (90% budget)
Level 3 (Minimal):    Cached/template responses only (95% budget)
Level 4 (Emergency):  Pre-written fallbacks only, no AI calls (100% budget or provider down)
```

### Human Escalation Triggers

Automatic human review is triggered for:
- Any CRISIS or CHILD_SAFETY classification
- Father expressing dissatisfaction 3+ times in a conversation
- Response validation failing 3 consecutive times
- Father sending 50+ messages in a single day
- Any single conversation exceeding 20 exchanges without resolution

Escalation SLA:
- CRISIS: human review within 4 hours
- CHILD_SAFETY: human review within 2 hours
- Quality issues: review within 24 hours

---

## Testing Strategy

### Dual Testing Approach

This feature combines **property-based tests** for the pure logic components with **example-based unit tests** for specific scenarios, edge cases, and integration points.

### Property-Based Testing

**Library:** [jqwik](https://jqwik.net/) — the standard property-based testing library for Java/JUnit 5.

**Configuration:** Minimum 100 iterations per property test.

**Tag format:** `@Tag("Feature: ai-architecture-intelligence-layer, Property {N}: {description}")`

**Components tested with PBT:**

| Component | Properties Tested |
|---|---|
| ContextManager / TokenBudget | P1 (budget invariant), P2 (sliding window minimum) |
| DecisionEngine | P3 (priority ordering), P4 (phase constraints), P5 (4-hour gap) |
| MemoryInjector | P6 (score ordering), P7 (diversity), P8 (confidence floor) |
| MissionPlanner / DifficultyCalculator | P9 (difficulty bounds), P10 (cooldowns), P11 (child equity) |
| CostController | P12 (tier enforcement) |
| ModelRouter | P13 (routing determinism), P14 (fallback ordering) |
| OutputValidator | P15 (schema validation) |
| SafetyClassifier | P16 (classification completeness) |
| EvaluationEngine | P17 (quality score formula) |
| AbTestManager | P18 (group assignment determinism) |
| AiMetricsCollector | P19 (alert threshold detection) |

**Generator strategy:**
- `CoachingContext` generator: random fathers with 1-4 children, random phases, random engagement scores, random mission histories
- `Memory` generator: random content, categories, importance (1-10), confidence (0.0-1.0), ages (0-365 days)
- `Message` generator: random English or Hebrew text, random lengths, random timestamps
- `MissionHistory` generator: random sequences of completed/expired/skipped missions with categories and dates

### Example-Based Unit Tests

| Component | Test Focus |
|---|---|
| SafetyClassifier | Known crisis keywords detected (English and Hebrew), jailbreak patterns blocked, edge cases (ambiguous messages) |
| PromptAssembler | Specific prompt structures for each conversation type, placeholder resolution |
| OutputContentFilter | Specific forbidden pattern detection (shame, diagnoses, PII) |
| MissionPlanner | Specific temporal context handling (weekday vs weekend, morning vs evening) |
| ModelRouter | Specific error scenarios (timeout, rate limit, 500 error) |

### Integration Tests

| Scope | What's Tested |
|---|---|
| AiIntelligenceService (facade) | End-to-end: inbound message → classification → decision → prompt assembly → mock LLM → validation → response |
| Model Router + OpenAI | Real API call with test prompt (rate-limited, CI-only with API key) |
| Telemetry Emitter → Database | Telemetry records correctly persisted to ai_telemetry table |
| Cost Controller + Daily Usage | Budget tracking accumulates correctly across multiple calls |
| Prompt Registry | Version retrieval, A/B group assignment, rollback logic |

### Test Infrastructure

```xml
<!-- Additional test dependencies (pom.xml) -->
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.8.5</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.5.4</version>
    <scope>test</scope>
</dependency>
```

- **jqwik**: Property-based testing (all 19 properties)
- **Testcontainers**: PostgreSQL for integration tests (telemetry, cost tracking, prompt registry)
- **WireMock**: Mock LLM provider APIs for deterministic integration tests

### Coverage Targets

- Pure logic components (Decision Engine, Memory Injector, Mission Planner, Cost Controller, Difficulty Calculator, Category Scorer): **≥ 90% line coverage**
- Adapter/integration code (Model Router adapters, Telemetry Emitter): **≥ 75% line coverage**
- Overall AI package: **≥ 85% line coverage**

### CI Pipeline Integration

1. Unit + Property tests run on every PR (< 2 minutes)
2. Integration tests run on merge to main (< 5 minutes, requires Testcontainers)
3. LLM API smoke test runs nightly (validates connectivity and basic response quality)
