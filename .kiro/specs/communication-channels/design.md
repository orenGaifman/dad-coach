# Technical Design — Communication Channels

## Architecture

### Overview

The Communication Channel layer implements the transport abstraction between external messaging providers and the Conversation Engine. It normalizes inbound webhooks into internal messages, delivers outbound messages to the correct provider, tracks delivery status, manages session windows, and handles media lifecycle. WhatsApp Cloud API is the first provider.

Built within the Spring Boot monolith with a Channel_Adapter pattern enabling future provider additions without modifying the orchestration layer.

### Architecture Decisions

**AD-1: Channel Adapter Pattern** — Each provider is implemented as a Spring bean implementing the `ChannelAdapter` interface. The `ChannelRouter` selects the adapter based on the father's primary Communication_Endpoint. New channels require only a new adapter + registration.

**AD-2: Webhook Endpoint per Provider** — Each provider has its own webhook controller. Signature verification happens at the controller level before any business logic. Verified payloads are normalized and forwarded to the Conversation Engine.

**AD-3: Outbound via Spring WebClient** — Outbound API calls to providers use Spring's reactive `WebClient` with timeout and retry configuration. This allows non-blocking I/O for delivery while the pipeline itself is synchronous.

**AD-4: Session Window in Database** — WhatsApp session window state (opens_at, closes_at) is tracked per Communication_Endpoint in PostgreSQL. Evaluated before every outbound delivery.

**AD-5: Media Storage via Object-Like Table** — Media assets (images, audio, video, documents) are stored in a dedicated table with binary content in a BYTEA column (suitable for launch scale). At growth scale, this migrates to object storage with minimal code change.

### Package Structure

```
com.dadcoach.whatsapp/
├── WhatsAppWebhookController.java     # Webhook endpoint + signature verification
├── WhatsAppAdapter.java               # ChannelAdapter implementation
├── WhatsAppSignatureVerifier.java     # HMAC-SHA256 verification
├── WhatsAppMessageParser.java         # Raw payload → InboundMessageDto
├── WhatsAppMessageFormatter.java      # OutboundMessageDto → WhatsApp API format
├── WhatsAppApiClient.java             # WebClient wrapper for Cloud API
├── dto/
│   ├── WhatsAppWebhookPayload.java    # Deserialized webhook JSON
│   └── WhatsAppSendRequest.java       # Outbound API request format
└── config/
    └── WhatsAppProperties.java        # API credentials, webhook secret

com.dadcoach.channel/
├── ChannelAdapter.java                # Interface (receive, send, session, capabilities)
├── ChannelRouter.java                 # Routes to correct adapter by endpoint
├── CommunicationEndpoint.java         # JPA entity (father ↔ channel identity)
├── CommunicationEndpointRepository.java
├── delivery/
│   ├── DeliveryService.java           # Outbound delivery orchestration
│   ├── DeliveryStatus.java            # Enum: PENDING, SENT, DELIVERED, READ, FAILED
│   ├── DeliveryRecord.java            # JPA entity tracking per-message status
│   └── DeliveryRetryService.java      # Transport-level retry (2s-32s backoff)
├── session/
│   └── SessionWindowService.java      # Session window tracking + evaluation
├── template/
│   ├── TemplateRegistry.java          # Approved templates store
│   └── TemplateMessage.java           # JPA entity
├── media/
│   ├── MediaService.java              # Download, store, retrieve, cleanup
│   └── MediaAsset.java                # JPA entity
├── capability/
│   ├── ChannelCapabilities.java       # Value object: supported features per adapter
│   └── MessageDowngrader.java         # Automatic type downgrade when unsupported
└── dto/
    ├── InboundMessageDto.java         # Normalized internal format
    └── OutboundMessageDto.java        # Normalized internal format
```

## Components and Interfaces

### ChannelAdapter Interface

```java
public interface ChannelAdapter {
    String getChannelName();  // "WHATSAPP", "SMS", etc.
    ChannelCapabilities getCapabilities();
    InboundMessageDto normalizeInbound(Object rawPayload);
    DeliveryResult sendMessage(OutboundMessageDto message, String channelIdentity);
    SessionState getSessionState(String channelIdentity);
    DeliveryStatus getDeliveryStatus(String providerMessageId);
}
```

### DeliveryService (Outbound Orchestration)

```java
@Service
public class DeliveryService {
    public DeliveryResult deliver(OutboundMessageDto message) {
        // 1. Resolve endpoint for father
        var endpoint = endpointRepository.findPrimaryByFatherId(message.fatherId());
        
        // 2. Check session window
        var session = sessionWindowService.getState(endpoint);
        if (session.isClosed() && !message.isTemplate()) {
            return DeliveryResult.rejected(SESSION_CLOSED);
        }
        
        // 3. Check capabilities + downgrade if needed
        var adapter = channelRouter.getAdapter(endpoint.getChannel());
        var finalMessage = messageDowngrader.downgradeIfNeeded(message, adapter.getCapabilities());
        
        // 4. Deliver
        return adapter.sendMessage(finalMessage, endpoint.getChannelIdentity());
    }
}
```

## Data Models

### Communication Tables

```sql
CREATE TABLE communication_endpoints (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id           UUID NOT NULL REFERENCES fathers(id),
    channel             VARCHAR(20) NOT NULL,  -- WHATSAPP, SMS
    channel_identity    VARCHAR(50) NOT NULL,  -- E.164 phone for WhatsApp
    is_primary          BOOLEAN NOT NULL DEFAULT TRUE,
    session_opens_at    TIMESTAMPTZ,
    session_closes_at   TIMESTAMPTZ,
    last_active_at      TIMESTAMPTZ,
    registered_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(channel, channel_identity)
);

CREATE INDEX idx_endpoints_father ON communication_endpoints(father_id);

CREATE TABLE delivery_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id          UUID NOT NULL,  -- internal message_id from conversation_messages
    father_id           UUID NOT NULL,
    channel             VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(100),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    direction           VARCHAR(10) NOT NULL,  -- OUTBOUND
    failure_reason      VARCHAR(100),
    retry_count         INTEGER NOT NULL DEFAULT 0,
    sent_at             TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    read_at             TIMESTAMPTZ,
    failed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_status ON delivery_records(status) WHERE status IN ('PENDING','SENT');
CREATE INDEX idx_delivery_provider_id ON delivery_records(provider_message_id);

CREATE TABLE template_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_name   VARCHAR(100) NOT NULL UNIQUE,
    language        VARCHAR(10) NOT NULL DEFAULT 'es',
    category        VARCHAR(20) NOT NULL,
    body            TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    max_variables   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE media_assets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL,
    message_id      UUID NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    file_size       INTEGER NOT NULL,
    content         BYTEA NOT NULL,
    downloaded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL  -- downloaded_at + 90 days
);

CREATE INDEX idx_media_expires ON media_assets(expires_at);
```

## Error Handling

| Scenario | Handling |
|----------|----------|
| Webhook signature invalid | Reject immediately (401); log source IP + timestamp |
| Provider delivery timeout | Transport retry: 2s, 4s, 8s, 16s, 32s (5 max) |
| Session window closed | Return SESSION_CLOSED to Conversation Engine; it decides template/defer |
| Template not APPROVED | Return TEMPLATE_UNAVAILABLE; Conversation Engine uses alternative |
| Media download fails | Deliver message without media; log failure; text still delivered |
| Circuit breaker trips (10 consecutive failures in 5 min) | Pause outbound 60s; probe; resume |
| Provider rate limit | Pause for indicated backoff; queue pending messages |

## Correctness Properties

- Webhook signature is verified BEFORE any parsing or processing — no unsigned data enters the system
- Session window is checked BEFORE every outbound delivery — no messages sent outside window without template
- The Communication Channel NEVER interprets message content — it normalizes and transports only
- Delivery status is correlated by provider_message_id — status updates for unknown IDs are discarded
- Media has a hard 90-day retention; cleanup job deletes expired assets daily
- The Conversation Engine addresses messages to `father_id`; endpoint resolution is fully owned by this layer

## Requirement Traceability

| Requirement | Design Element |
|-------------|---------------|
| Req 1: Channel Abstraction | `ChannelAdapter` interface + `ChannelRouter` + endpoint model |
| Req 2: Inbound Lifecycle | `WhatsAppWebhookController` → verify → parse → normalize → deliver to engine |
| Req 3: Outbound Lifecycle | `DeliveryService` → session check → format → send → track |
| Req 4: Message Types | Parser/formatter handle all types; `MessageDowngrader` for unsupported |
| Req 5: Delivery Status | `DeliveryRecord` entity + status update from provider webhooks |
| Req 6: Media | `MediaService` + `media_assets` table + 90-day cleanup |
| Req 7: Retries | `DeliveryRetryService` (transport) + business retry via outbox |
| Req 8: Session Rules | `SessionWindowService` + `session_opens_at/closes_at` on endpoint |
| Req 9: Templates | `TemplateRegistry` + `template_messages` table |
| Req 10: Security | `WhatsAppSignatureVerifier` + credential protection in properties |
| Req 11: Formatting | `WhatsAppMessageFormatter` (WhatsApp markdown, char limits, emoji) |
| Req 12: Extensibility | Adapter pattern; new channel = new adapter bean + endpoint entry |
| Req 13: Monitoring | Metrics emitted by DeliveryService; alerts consumed by SPEC-009 |
| Req 14: Cross-Spec | Sole interface with Conversation Engine is InboundMessageDto/OutboundMessageDto |
