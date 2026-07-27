# Requirements Document

## Introduction

**SPEC-007: Application API**

This specification defines the application interface layer of the Dad Coach platform. It is the authoritative product specification for how external clients, administrative tools, and internal services interact with the application through stable, versioned APIs.

This document defines ONLY the business contract — what operations are available, who may invoke them, what data is exchanged, and what behavior is guaranteed. It does not define transport protocols, serialization formats, framework bindings, or infrastructure concerns.

**Scope boundaries:**
- SPEC-001 defines infrastructure and deployment
- SPEC-002 defines domain entities, state machines, and business rules
- SPEC-003 defines AI prompt assembly, model routing, and output contracts
- SPEC-004 defines memory lifecycle, storage, and retrieval
- SPEC-005 defines conversation orchestration
- SPEC-006 defines communication channels and provider abstraction
- SPEC-007 (this document) defines the application's external and internal API contracts

**Ownership principle:** The API layer exposes domain capabilities to authorized consumers. It does NOT own business logic, state machines, AI behavior, memory management, or communication delivery. Those responsibilities remain with their owning specifications. The API layer owns: request validation, authorization enforcement, response shaping, pagination, versioning, and error formatting.

**Boundary with SPEC-005:** The Conversation_Engine (SPEC-005) handles real-time coaching interactions triggered by inbound messages through the Communication_Channel (SPEC-006). The Application API provides complementary access for administrative operations, profile management, and read access to system state. The API does NOT replace or bypass the coaching conversation flow.

**Boundary with SPEC-006:** The Communication_Channel handles inbound/outbound messaging with fathers via messaging providers. The Application API provides a separate access surface for non-messaging interactions (admin tools, dashboards, integrations).

## Glossary

- **Application_API**: The set of operations exposed by the Dad Coach platform to authorized consumers
- **API_Consumer**: Any client that invokes the Application_API (admin dashboard, mobile app, internal service, monitoring tool)
- **Actor**: The authenticated identity performing an API operation (FATHER, ADMIN, SERVICE, SYSTEM)
- **Resource**: A domain entity or aggregate exposed through the API (Father, Child, Conversation, Mission, Goal, Memory)
- **Operation**: A single API action on a resource (read, create, update, delete, search)
- **API_Version**: A stable contract revision that guarantees backward compatibility within the same major version
- **Request_Validation**: The process of verifying that an API request conforms to the required structure and business constraints before processing
- **Business_Error**: An error caused by a domain rule violation (e.g., maximum children exceeded, invalid state transition)
- **Idempotency_Key**: A client-provided unique identifier enabling safe retry of non-idempotent operations
- **Pagination_Cursor**: An opaque token representing a position in a result set for cursor-based pagination
- **Access_Scope**: The set of resources and operations an Actor is authorized to perform
- **Sensitive_Field**: A data field that requires special handling (masking, omission, or restricted access) in API responses
- **Rate_Limit**: A constraint on the number of API requests an Actor may make within a time window

---

## Requirements

### Requirement 1: API Scope and Boundaries

**User Story:** As a product owner, I want clear boundaries for each API surface, so that consumers know what is available and what access level is required.

#### Acceptance Criteria

1. THE Application_API SHALL define three distinct API surfaces:

   | Surface | Purpose | Consumers | Access Model |
   |---------|---------|-----------|-------------|
   | Father API | Self-service operations for fathers (profile, children, goals, progress) | Father (via future mobile app or web portal) | Authenticated as the specific father |
   | Admin API | Operational management, monitoring, and support operations | Admin dashboard, support agents | Authenticated as admin with role-based permissions |
   | Service API | Internal service-to-service operations (metrics, events, health) | Internal subsystems, monitoring tools | Authenticated via service credentials |

2. THE Father API SHALL expose ONLY operations that a father can perform on their own data. A father SHALL NEVER access another father's resources through this surface.

3. THE Admin API SHALL expose operations for support, monitoring, and system management. Admin operations may access any father's data (read) and perform privileged mutations (pause, delete, override settings).

4. THE Service API SHALL expose operations needed by internal subsystems that do not have direct domain access. Service operations are not user-facing and may return internal representations.

5. THE Application_API SHALL NOT expose:
   - Direct AI prompt manipulation (owned by SPEC-003)
   - Direct memory creation or lifecycle management (owned by SPEC-004, triggered through conversation flow)
   - Direct conversation orchestration bypassing the pipeline (owned by SPEC-005)
   - Direct communication channel management (owned by SPEC-006)

6. THE Application_API SHALL expose read access to the state managed by other subsystems (conversation history, memory summaries, delivery status) without owning or modifying that state's lifecycle.

---

### Requirement 2: Resource Model

**User Story:** As a product owner, I want a clear resource model, so that API consumers interact with well-defined entities that map to the domain model.

#### Acceptance Criteria

1. THE Application_API SHALL expose the following resources:

   | Resource | Owner Spec | API Operations | Notes |
   |----------|-----------|----------------|-------|
   | Father | SPEC-002 | Read, Update, Delete | Profile and preferences management |
   | Child | SPEC-002 | Read, Create, Update, Archive | Linked to owning father |
   | Goal | SPEC-002 | Read, Create, Update, Archive | Parenting objectives |
   | Mission | SPEC-002 | Read, List | Read-only; missions are created by the coaching flow |
   | Conversation | SPEC-005 | Read, List | Read-only; conversations are created by the orchestration pipeline |
   | Memory | SPEC-004 | Read, List, Delete | Read + delete only; memories are created by extraction pipeline |
   | Notification | SPEC-002 | Read, List | Read-only delivery history |
   | System_Health | SPEC-001 | Read | System status and readiness |

2. WHEN exposing a resource, THE Application_API SHALL present a stable external representation that may differ from the internal domain model. Internal fields not relevant to the consumer (e.g., embedding vectors, internal state flags) SHALL be omitted.

3. THE Application_API SHALL expose resources as self-contained representations. Nested resources (e.g., a father's children) SHALL be accessible both through the parent resource and as independent resources with their own identifiers.

4. THE Application_API SHALL NOT expose:
   - Internal AI telemetry records (SPEC-003 Requirement 16) — available only through the Service API for monitoring tools
   - Memory audit logs (SPEC-004 Requirement 18) — available only through the Admin API
   - Communication endpoint details (SPEC-006) — available only through the Admin API
   - Raw conversation transcripts with system prompt content — only the father-visible message history

5. THE Application_API SHALL return resource identifiers as opaque unique identifiers. Consumers SHALL NOT depend on identifier format, ordering, or internal structure.

---

### Requirement 3: Father Resource Operations

**User Story:** As a father, I want to manage my profile and preferences through the API, so that I can view and update my coaching settings.

#### Acceptance Criteria

1. THE Father API SHALL support the following operations on the Father resource:

   | Operation | Description | Allowed Actors |
   |-----------|------------|---------------|
   | Get Profile | Retrieve the father's full profile | FATHER (own), ADMIN |
   | Update Profile | Modify display_name, timezone, coaching_style, preferred_coaching_time | FATHER (own), ADMIN |
   | Get Progress | Retrieve engagement_score, coaching_streak, mission_completion_rate, phase | FATHER (own), ADMIN |
   | Request Pause | Transition status to PAUSED for a specified duration (max 30 days) | FATHER (own), ADMIN |
   | Resume | Transition status from PAUSED to ACTIVE | FATHER (own), ADMIN |
   | Request Deletion | Initiate account deletion (per SPEC-002 Requirement 1 criteria 6) | FATHER (own), ADMIN |
   | Export Data | Request full data export (per SPEC-004 Requirement 17 criteria 7) | FATHER (own) |

2. THE Admin API SHALL support additional Father operations:

   | Operation | Description | Required Permission |
   |-----------|------------|-------------------|
   | List Fathers | Paginated list with filtering (status, phase, engagement) | ADMIN:READ |
   | Search Fathers | Search by name, phone (partial match) | ADMIN:READ |
   | Get Father Detail | Full profile including internal metadata | ADMIN:READ |
   | Override Status | Force status transition (for support cases) | ADMIN:WRITE |
   | Override Settings | Modify any father setting | ADMIN:WRITE |

3. WHEN a Father requests deletion, THE Application_API SHALL initiate the deletion flow defined in SPEC-002 Requirement 1 criteria 6 and SPEC-004 Requirement 17 criteria 6. The API returns an acknowledgment; actual data erasure is asynchronous (within 72 hours).

4. WHEN a Father requests a pause, THE Application_API SHALL validate that the requested duration is between 1 and 30 days and the father's current status permits the transition (per SPEC-002 Requirement 11 criteria 1).

---

### Requirement 4: Child Resource Operations

**User Story:** As a father, I want to manage my children's profiles, so that coaching is correctly personalized for each child.

#### Acceptance Criteria

1. THE Father API SHALL support the following operations on the Child resource:

   | Operation | Description | Validation |
   |-----------|------------|-----------|
   | List Children | All children for the authenticated father | — |
   | Get Child | Single child detail | Must belong to authenticated father |
   | Create Child | Register a new child | Max 8 per father (SPEC-002 Req 2 criteria 2); birth_date 0-18 years ago |
   | Update Child | Modify name, interests, challenges | Must belong to authenticated father |
   | Archive Child | Soft-remove from active coaching | Must belong to authenticated father |

2. WHEN creating a child, THE Application_API SHALL validate:
   - Name is non-empty (1-100 characters)
   - Birth_date is a valid date between 0 and 18 years in the past (per SPEC-002 Requirement 2 criteria 4)
   - Father has fewer than 8 active children (per SPEC-002 Requirement 2 criteria 2)

3. WHEN archiving a child, THE Application_API SHALL transition the child to ARCHIVED status. This excludes the child from mission generation (per SPEC-002 Requirement 2 criteria 5) and soft-archives related memories (per SPEC-004 Requirement 23 criteria 5). The operation is reversible.

4. THE Application_API SHALL compute and return a child's age dynamically from birth_date — never as a stored field (per SPEC-002 Requirement 2 criteria 3).


---

### Requirement 5: Goal, Mission, Conversation, and Memory Operations

**User Story:** As a father, I want to view my coaching history, goals, and memories, so that I can track my progress and understand what the system knows about my family.

#### Acceptance Criteria

1. THE Father API SHALL support the following Goal operations:

   | Operation | Description | Notes |
   |-----------|------------|-------|
   | List Goals | All goals for the authenticated father | Includes progress percentage |
   | Get Goal | Single goal detail with related missions | — |
   | Create Goal | Define a new parenting goal | Category, description, priority required |
   | Update Goal | Modify description or priority | Cannot modify completed goals |
   | Archive Goal | Mark goal as abandoned | Transitions to archived; coaching adjusts |

2. THE Father API SHALL support the following Mission operations (read-only):

   | Operation | Description | Notes |
   |-----------|------------|-------|
   | List Missions | Paginated mission history | Filterable by status, child, date range |
   | Get Mission | Single mission detail | Includes outcome_rating if completed |
   | Get Active Mission | Current active mission (if any) | Returns null/empty if no active mission |

3. THE Father API SHALL support the following Conversation operations (read-only):

   | Operation | Description | Notes |
   |-----------|------------|-------|
   | List Conversations | Paginated conversation history | Filterable by type, status, date range |
   | Get Conversation | Conversation detail with message history | Only father-visible messages (no system prompts) |

4. THE Father API SHALL support the following Memory operations:

   | Operation | Description | Notes |
   |-----------|------------|-------|
   | List Memories | All active memories grouped by category | Excludes SUPERSEDED, EXPIRED, DELETED |
   | Get Memory | Single memory detail | — |
   | Delete Memory | Request deletion of a specific memory | Triggers SPEC-004 deletion flow |

5. THE Admin API SHALL support all Father API operations plus:
   - List all memories for a father (including ARCHIVED)
   - View memory audit history
   - View conversation transcripts with full metadata
   - View mission generation history and difficulty adjustments

6. THE Application_API SHALL NOT expose operations that create missions, create conversations, or create memories directly. These are created exclusively by the orchestration pipeline (SPEC-005), Mission_Planner (SPEC-003), and Memory_System extraction (SPEC-004) respectively.

---

### Requirement 6: Authentication and Authorization

**User Story:** As a product owner, I want access strictly controlled by actor type and permission, so that fathers can only access their own data and administrative operations require elevated credentials.

#### Acceptance Criteria

1. THE Application_API SHALL require authentication for every operation except System_Health (liveness/readiness probes).

2. THE Application_API SHALL support the following Actor types:

   | Actor | Identity Basis | Access Scope |
   |-------|---------------|-------------|
   | FATHER | Authenticated father identity | Own profile, own children, own goals, own missions, own conversations, own memories |
   | ADMIN | Administrative credential with role | All fathers' data (read); privileged mutations per permission |
   | SERVICE | Service-to-service credential | Internal operations, metrics, health detail |
   | SYSTEM | Internal scheduled processes | Event triggers, batch operations (no API consumer) |

3. THE Application_API SHALL enforce resource ownership for FATHER actors: every request SHALL verify that the target resource belongs to the authenticated father. Access to another father's resources SHALL return a Not Found response (not a Forbidden response — to prevent enumeration).

4. THE Admin API SHALL enforce role-based permissions:
   - ADMIN:READ — view any father's data, conversations, memories, audit logs
   - ADMIN:WRITE — modify father status, override settings, force state transitions
   - ADMIN:DELETE — initiate account deletion, purge data
   An admin without the required permission for an operation SHALL receive a Forbidden response.

5. THE Service API SHALL authenticate using service credentials (not user credentials). Service operations are scoped to internal system needs and SHALL NOT be exposed to external consumers.

6. THE Application_API SHALL NOT implement its own identity provider. Authentication mechanism (tokens, sessions, certificates) is a Tech Design decision. This specification defines only the authorization model (who can do what).

7. THE Application_API SHALL log every authenticated request with: actor_type, actor_id, operation, target_resource_id, timestamp, and result (success/failure). This audit trail is retained per the operational requirements of SPEC-001.

---

### Requirement 7: Validation Rules

**User Story:** As a product owner, I want all API requests validated before processing, so that invalid data never reaches the domain layer and errors are clear to consumers.

#### Acceptance Criteria

1. THE Application_API SHALL validate every request in two phases:
   - **Structural validation**: Required fields present, correct types, within length/range bounds
   - **Business validation**: Domain rule compliance (state machine transitions valid, limits not exceeded, references resolvable)

2. THE Application_API SHALL enforce the following field validations for Father updates:
   - display_name: 1-100 characters, non-empty
   - timezone: valid IANA timezone identifier
   - coaching_style: one of GENTLE, BALANCED, DIRECT, MOTIVATIONAL
   - preferred_coaching_time: valid HH:MM format, between 00:00 and 23:59

3. THE Application_API SHALL enforce the following field validations for Child creation/update:
   - name: 1-100 characters, non-empty
   - birth_date: valid date, between 0 and 18 years in the past
   - interests: array of strings, each 1-100 characters, maximum 20 items
   - challenges: array of strings, each 1-200 characters, maximum 10 items

4. THE Application_API SHALL enforce the following field validations for Goal creation:
   - description: 1-500 characters, non-empty
   - category: one of CONNECTION, COMMUNICATION, DISCIPLINE, EDUCATION, HEALTH, EMOTIONAL, INDEPENDENCE, FUN, ROUTINE, CUSTOM
   - priority: integer 1-5

5. WHEN structural validation fails, THE Application_API SHALL reject the request immediately without invoking the domain layer, returning a validation error that identifies all invalid fields.

6. WHEN business validation fails (e.g., maximum children exceeded, invalid state transition), THE Application_API SHALL return a business error with a specific error code and human-readable message explaining the constraint.

7. THE Application_API SHALL validate all identifier references (father_id, child_id, goal_id, mission_id) before processing. Unresolvable references SHALL return Not Found.

---

### Requirement 8: Idempotency

**User Story:** As a product owner, I want mutating operations safely retryable, so that network failures don't cause duplicate or inconsistent state.

#### Acceptance Criteria

1. THE Application_API SHALL classify operations by idempotency:
   - **Naturally idempotent** (GET, DELETE by id): safe to retry without additional mechanism
   - **Conditionally idempotent** (PUT/update): the request replaces the full resource state; retries produce the same result
   - **Non-idempotent** (POST/create): require an Idempotency_Key for safe retry

2. FOR non-idempotent operations (creation of children, goals), THE Application_API SHALL accept an optional Idempotency_Key provided by the consumer. If a request with the same key has already been processed successfully, the API SHALL return the original response without re-executing the operation.

3. THE Application_API SHALL retain Idempotency_Key → response mappings for 24 hours, after which the key expires and a new request with the same key is treated as a new operation.

4. WHEN two requests with the same Idempotency_Key arrive concurrently, THE Application_API SHALL process only one and return the result to both. The second request waits for the first to complete rather than executing in parallel.

5. THE Application_API SHALL NOT require Idempotency_Keys for read operations or operations that are naturally idempotent (deletes, full-resource updates).

---

### Requirement 9: Error Model

**User Story:** As a product owner, I want a consistent error structure across all API operations, so that consumers can build reliable error handling without special-casing per endpoint.

#### Acceptance Criteria

1. THE Application_API SHALL return all errors in a consistent structure containing:
   - error_code: a stable, machine-readable identifier (e.g., VALIDATION_FAILED, RESOURCE_NOT_FOUND, STATE_TRANSITION_INVALID)
   - message: a human-readable description of the error (in English)
   - details: an optional array of field-level errors (for validation failures)
   - request_id: the unique identifier of the failed request (for support correlation)
   - retryable: boolean indicating whether the consumer should retry

2. THE Application_API SHALL define the following error categories:

   | Category | Error Codes | Retryable |
   |----------|------------|-----------|
   | Validation | VALIDATION_FAILED, FIELD_REQUIRED, FIELD_INVALID, FIELD_TOO_LONG | No |
   | Authorization | UNAUTHORIZED, FORBIDDEN, TOKEN_EXPIRED | No (except TOKEN_EXPIRED: retry with refresh) |
   | Not Found | RESOURCE_NOT_FOUND | No |
   | Conflict | RESOURCE_CONFLICT, STATE_TRANSITION_INVALID, DUPLICATE_RESOURCE | No |
   | Business Rule | LIMIT_EXCEEDED, OPERATION_NOT_ALLOWED, PRECONDITION_FAILED | No |
   | Rate Limit | RATE_LIMIT_EXCEEDED | Yes (after backoff) |
   | Server Error | INTERNAL_ERROR, SERVICE_UNAVAILABLE | Yes |

3. WHEN a validation error occurs with multiple invalid fields, THE Application_API SHALL return ALL field-level errors in a single response (not one at a time), enabling consumers to fix all issues before retrying.

4. WHEN a resource is not found, THE Application_API SHALL return RESOURCE_NOT_FOUND regardless of whether the resource never existed or the actor lacks access. This prevents resource enumeration.

5. WHEN a business rule prevents an operation (e.g., maximum 8 children, cannot pause a CHURNED father), THE Application_API SHALL return the specific error_code and a message explaining which rule was violated.

6. THE Application_API SHALL never expose internal stack traces, internal identifiers, or system implementation details in error responses. Errors are informative but safe.

---

### Requirement 10: Pagination, Filtering, and Sorting

**User Story:** As a product owner, I want list operations efficient and flexible, so that consumers can retrieve exactly the data they need without overwhelming the system.

#### Acceptance Criteria

1. THE Application_API SHALL use cursor-based pagination for all list operations. Responses include:
   - items: the current page of results
   - next_cursor: opaque token for the next page (null if no more results)
   - has_more: boolean indicating additional pages exist

2. THE Application_API SHALL define a default page size (20 items) and a maximum page size (100 items). Consumers may request a specific page size within this range.

3. THE Application_API SHALL support filtering on list operations:
   - Missions: by status, child_id, category, date range (assigned_at)
   - Conversations: by type, status, date range (created_at)
   - Memories: by category, subject_type, child_id, state
   - Goals: by status (active, completed, archived), category
   - Fathers (Admin): by status, coaching_phase, engagement_score range

4. THE Application_API SHALL support sorting on list operations with a default sort order per resource:
   - Missions: default by assigned_at descending (most recent first)
   - Conversations: default by created_at descending
   - Memories: default by importance_score descending, then confidence_score descending
   - Goals: default by priority ascending, then created_at descending

5. THE Application_API SHALL return total count information ONLY when explicitly requested by the consumer (via a separate count operation or parameter). Default list operations return only the current page and pagination cursors.

6. THE Application_API SHALL enforce maximum response payload sizes. If a single resource representation exceeds 64KB, truncate large text fields and provide a link/indicator for full content retrieval.


---

### Requirement 11: API Versioning

**User Story:** As a product owner, I want a clear versioning strategy, so that API changes never break existing consumers and new capabilities can be introduced safely.

#### Acceptance Criteria

1. THE Application_API SHALL use explicit version identification for all operations. The versioning mechanism (path prefix, header, or parameter) is a Tech Design decision; this specification defines the versioning policy.

2. THE Application_API SHALL follow these versioning rules:
   - **Major version** (v1 → v2): Breaking changes — removed fields, changed types, altered behavior. Requires consumer migration with a defined transition period.
   - **Minor version** (additive): New optional fields, new operations, new filter options. Backward compatible — existing consumers are unaffected.
   - **Patch version**: Bug fixes, documentation corrections. No contract change.

3. THE Application_API SHALL maintain backward compatibility within a major version: once a field or behavior is published in v1, it cannot be removed or changed until v2.

4. WHEN a new major version is released, THE Application_API SHALL support the previous major version for a minimum of 6 months (transition period). After the transition period, the old version may be deprecated and eventually removed.

5. THE Application_API SHALL communicate deprecation clearly: deprecated operations return a deprecation warning header/indicator, and documentation marks the sunset date.

6. THE Application_API SHALL launch at version 1. No version 0 or unversioned operations are permitted in production.

---

### Requirement 12: Performance Requirements

**User Story:** As a product owner, I want performance expectations defined, so that the API meets user experience requirements without over-engineering.

#### Acceptance Criteria

1. THE Application_API SHALL define performance targets per operation category:

   | Category | Target Latency (p95) | Notes |
   |----------|---------------------|-------|
   | Single resource read (GET by id) | < 500ms | Direct retrieval |
   | List operations (paginated) | < 1 second | For default page size |
   | Create/Update operations | < 1 second | Synchronous validation + persistence |
   | Delete operations | < 500ms | Acknowledgment; actual erasure may be async |
   | Search operations (Admin) | < 2 seconds | May involve complex filtering |
   | Health checks | < 100ms | Lightweight probes |

2. THE Application_API SHALL reject requests that would produce unbounded work. All list operations require pagination. Queries without reasonable bounds (e.g., "all conversations for all fathers") are not permitted on the Father API — only on the Admin API with mandatory filters.

3. THE Application_API SHALL define maximum payload sizes:
   - Request body: maximum 1 MB
   - Response body: maximum 5 MB (single response)
   - Individual text fields: maximum defined per field (see Requirement 7)
   Requests exceeding limits are rejected with PAYLOAD_TOO_LARGE error.

4. THE Application_API SHALL support conditional requests: if the resource has not changed since the consumer's last retrieval, the API may return a "not modified" indication without the full payload. The mechanism is a Tech Design decision.

5. THE Application_API SHALL enforce rate limits per Actor:
   - FATHER: configurable, default 60 requests per minute
   - ADMIN: configurable, default 300 requests per minute
   - SERVICE: configurable, default 1000 requests per minute
   When exceeded, return RATE_LIMIT_EXCEEDED with an indication of when the consumer may retry.

---

### Requirement 13: Security Requirements

**User Story:** As a product owner, I want the API secure by design, so that sensitive family data is protected and the system resists common attack vectors.

#### Acceptance Criteria

1. THE Application_API SHALL validate all input to prevent injection attacks. No consumer-provided value SHALL be passed directly to internal queries, commands, or templates without validation and sanitization.

2. THE Application_API SHALL classify response fields by sensitivity:

   | Sensitivity | Handling | Examples |
   |------------|---------|---------|
   | PUBLIC | Returned normally | display_name, coaching_style, child interests |
   | INTERNAL | Omitted from Father API responses; available in Admin/Service API | engagement_score internals, memory confidence_scores |
   | SENSITIVE | Masked or omitted; available only with explicit request and elevated permission | phone number (masked: +1***...***89), full memory content |
   | RESTRICTED | Never returned via API | memory embeddings, AI prompt content, system secrets |

3. THE Application_API SHALL mask phone numbers in responses: display only the country code and last 2 digits (e.g., "+1********89") unless the consumer is the owning FATHER or has ADMIN:READ permission.

4. THE Application_API SHALL enforce transport encryption for all API communication. Unencrypted requests SHALL be rejected.

5. THE Application_API SHALL include security headers in responses to prevent common attacks (clickjacking, content sniffing, XSS). Specific headers are a Tech Design decision.

6. THE Application_API SHALL log all failed authentication attempts with: source identifier, timestamp, and failure reason — without logging credentials or tokens.

7. THE Application_API SHALL reject requests containing credentials or tokens in query parameters. Credentials must be provided through secure mechanisms (headers, request body) only.

8. THE Application_API SHALL support CORS (Cross-Origin Resource Sharing) configuration for web-based consumers, restricting origins to explicitly allowed domains.

---

### Requirement 14: Audit Requirements

**User Story:** As a product owner, I want all significant API operations auditable, so that I can investigate issues, demonstrate compliance, and track system usage.

#### Acceptance Criteria

1. THE Application_API SHALL audit every mutating operation (create, update, delete, status change) with:
   - request_id: unique identifier for the request
   - actor_type and actor_id: who performed the operation
   - operation: what was done
   - resource_type and resource_id: what was affected
   - timestamp: when it occurred
   - result: success or failure (with error_code if failed)
   - changes: for updates, the fields that changed (old value → new value), excluding sensitive content

2. THE Application_API SHALL audit all Admin API operations regardless of whether they mutate state (reads on other fathers' data are also audited).

3. THE Application_API SHALL retain API audit logs for 2 years as a product policy, consistent with SPEC-004 Requirement 18 criteria 3.

4. THE Application_API SHALL NOT log request or response bodies containing sensitive data (memory content, conversation messages, personal details). Only metadata (types, counts, identifiers) appears in audit logs.

5. THE Admin API SHALL expose audit log access:
   - Search by actor, by resource, by operation, by time range
   - Paginated results
   - Required permission: ADMIN:READ

---

### Requirement 15: System Health and Monitoring

**User Story:** As a product owner, I want system health accessible through the API, so that monitoring tools and orchestrators can determine application readiness.

#### Acceptance Criteria

1. THE Application_API SHALL expose health check operations accessible without authentication:
   - Liveness: indicates the application process is running (per SPEC-001 Requirement 9 criteria 1)
   - Readiness: indicates the application can process requests (per SPEC-001 Requirement 9 criteria 2)

2. THE Service API SHALL expose detailed health information (authenticated):
   - Component status: domain layer, AI provider connectivity, memory system, communication channels
   - Current metrics: active conversations, messages processed today, AI call budget remaining
   - Subsystem latency: p50 and p95 for key operations

3. THE health endpoints SHALL NOT expose version numbers, dependency details, or internal architecture information to unauthenticated consumers. Detailed health is only available through the authenticated Service API.

4. THE Application_API SHALL respond to health checks within 100ms. Health endpoints SHALL NOT perform expensive computations or external calls — they report cached status only.

---

### Requirement 16: Cross-Spec Compatibility

**User Story:** As an architect, I want explicit verification that the API layer is compatible with all other specifications, so that no ownership conflicts or duplicated responsibilities exist.

#### Acceptance Criteria

1. THE Application_API SHALL NOT own or enforce domain state machines (owned by SPEC-002). The API validates that a requested transition is legal by querying the domain layer; the domain layer makes the final determination.

2. THE Application_API SHALL NOT own conversation orchestration logic (owned by SPEC-005). The API provides read access to conversation state but cannot start, continue, or complete conversations. Conversations are triggered only through inbound messages via the Communication_Channel (SPEC-006).

3. THE Application_API SHALL NOT own memory lifecycle logic (owned by SPEC-004). The API can request memory deletion (which the Memory_System executes per its own rules), but cannot create, update confidence, or force state transitions on memories.

4. THE Application_API SHALL NOT own AI behavior (owned by SPEC-003). No API operation directly invokes AI generation, modifies prompts, or alters model routing.

5. THE Application_API SHALL NOT own communication delivery (owned by SPEC-006). The API does not send messages to fathers — it manages data. Message delivery is exclusively handled by the Communication_Channel layer triggered by the Conversation_Engine.

6. THE Application_API SHALL respect all business rules defined in SPEC-002:
   - Maximum 8 children per father (Requirement 2 criteria 2)
   - Father status transitions per state machine (Requirement 11 criteria 1)
   - Goal categories and priorities (Requirement 9)
   - Pause duration maximum 30 days (Requirement 1 criteria 7)

7. THE Application_API SHALL use the memory retrieval metadata contract defined in SPEC-004 Requirement 19 when exposing memories to consumers.

8. THE Application_API SHALL respect the data privacy requirements of SPEC-004 Requirement 17:
   - Never expose one father's data to another father
   - Honor deletion requests within the 72-hour window
   - Provide data export capability within 30 days
   - Classify and protect sensitive fields

9. THE Application_API SHALL complement (not replace) the real-time coaching interaction flow. Fathers interact with coaching through messaging (SPEC-006 → SPEC-005). The API provides supplementary access for profile management, progress viewing, and self-service operations.

10. THE Application_API SHALL publish Business_Events for significant mutations (per SPEC-005 Requirement 11 pattern): FATHER_PROFILE_UPDATED, CHILD_CREATED, CHILD_ARCHIVED, GOAL_CREATED, GOAL_ARCHIVED, MEMORY_DELETED. These events enable other subsystems to react without polling.
