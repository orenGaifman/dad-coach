package com.dadcoach.ai.telemetry;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a single AI call telemetry record.
 * Every AI call is metered with input/output tokens, cost, latency,
 * validation status, and quality metrics.
 */
@Entity
@Table(name = "ai_telemetry")
public class AiTelemetryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "conversation_type", length = 30)
    private String conversationType;

    @Column(name = "interaction_type", nullable = false, length = 30)
    private String interactionType;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "model_provider", nullable = false, length = 20)
    private String modelProvider;

    @Column(name = "model_name", nullable = false, length = 50)
    private String modelName;

    @Column(name = "temperature")
    private Float temperature;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "estimated_cost_usd")
    private Float estimatedCostUsd;

    @Column(name = "total_latency_ms", nullable = false)
    private int totalLatencyMs;

    @Column(name = "llm_latency_ms")
    private Integer llmLatencyMs;

    @Column(name = "validation_passed", nullable = false)
    private boolean validationPassed;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "quality_score")
    private Float qualityScore;

    @Column(name = "safety_classification", length = 30)
    private String safetyClassification;

    @Column(name = "ab_test_group", length = 5)
    private String abTestGroup;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiTelemetryRecord() {
        // JPA requires no-arg constructor
    }

    private AiTelemetryRecord(Builder builder) {
        this.requestId = builder.requestId;
        this.fatherId = builder.fatherId;
        this.conversationId = builder.conversationId;
        this.conversationType = builder.conversationType;
        this.interactionType = builder.interactionType;
        this.promptVersion = builder.promptVersion;
        this.modelProvider = builder.modelProvider;
        this.modelName = builder.modelName;
        this.temperature = builder.temperature;
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
        this.estimatedCostUsd = builder.estimatedCostUsd;
        this.totalLatencyMs = builder.totalLatencyMs;
        this.llmLatencyMs = builder.llmLatencyMs;
        this.validationPassed = builder.validationPassed;
        this.fallbackUsed = builder.fallbackUsed;
        this.retryCount = builder.retryCount;
        this.qualityScore = builder.qualityScore;
        this.safetyClassification = builder.safetyClassification;
        this.abTestGroup = builder.abTestGroup;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public UUID getFatherId() { return fatherId; }
    public UUID getConversationId() { return conversationId; }
    public String getConversationType() { return conversationType; }
    public String getInteractionType() { return interactionType; }
    public String getPromptVersion() { return promptVersion; }
    public String getModelProvider() { return modelProvider; }
    public String getModelName() { return modelName; }
    public Float getTemperature() { return temperature; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public Float getEstimatedCostUsd() { return estimatedCostUsd; }
    public int getTotalLatencyMs() { return totalLatencyMs; }
    public Integer getLlmLatencyMs() { return llmLatencyMs; }
    public boolean isValidationPassed() { return validationPassed; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public int getRetryCount() { return retryCount; }
    public Float getQualityScore() { return qualityScore; }
    public String getSafetyClassification() { return safetyClassification; }
    public String getAbTestGroup() { return abTestGroup; }
    public Instant getCreatedAt() { return createdAt; }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID requestId;
        private UUID fatherId;
        private UUID conversationId;
        private String conversationType;
        private String interactionType;
        private String promptVersion;
        private String modelProvider;
        private String modelName;
        private Float temperature;
        private int inputTokens;
        private int outputTokens;
        private Float estimatedCostUsd;
        private int totalLatencyMs;
        private Integer llmLatencyMs;
        private boolean validationPassed;
        private boolean fallbackUsed;
        private int retryCount;
        private Float qualityScore;
        private String safetyClassification;
        private String abTestGroup;

        public Builder requestId(UUID requestId) { this.requestId = requestId; return this; }
        public Builder fatherId(UUID fatherId) { this.fatherId = fatherId; return this; }
        public Builder conversationId(UUID conversationId) { this.conversationId = conversationId; return this; }
        public Builder conversationType(String conversationType) { this.conversationType = conversationType; return this; }
        public Builder interactionType(String interactionType) { this.interactionType = interactionType; return this; }
        public Builder promptVersion(String promptVersion) { this.promptVersion = promptVersion; return this; }
        public Builder modelProvider(String modelProvider) { this.modelProvider = modelProvider; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder temperature(Float temperature) { this.temperature = temperature; return this; }
        public Builder inputTokens(int inputTokens) { this.inputTokens = inputTokens; return this; }
        public Builder outputTokens(int outputTokens) { this.outputTokens = outputTokens; return this; }
        public Builder estimatedCostUsd(Float estimatedCostUsd) { this.estimatedCostUsd = estimatedCostUsd; return this; }
        public Builder totalLatencyMs(int totalLatencyMs) { this.totalLatencyMs = totalLatencyMs; return this; }
        public Builder llmLatencyMs(Integer llmLatencyMs) { this.llmLatencyMs = llmLatencyMs; return this; }
        public Builder validationPassed(boolean validationPassed) { this.validationPassed = validationPassed; return this; }
        public Builder fallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; return this; }
        public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }
        public Builder qualityScore(Float qualityScore) { this.qualityScore = qualityScore; return this; }
        public Builder safetyClassification(String safetyClassification) { this.safetyClassification = safetyClassification; return this; }
        public Builder abTestGroup(String abTestGroup) { this.abTestGroup = abTestGroup; return this; }

        public AiTelemetryRecord build() {
            if (requestId == null) throw new IllegalArgumentException("requestId is required");
            if (fatherId == null) throw new IllegalArgumentException("fatherId is required");
            if (interactionType == null || interactionType.isBlank()) throw new IllegalArgumentException("interactionType is required");
            if (modelProvider == null || modelProvider.isBlank()) throw new IllegalArgumentException("modelProvider is required");
            if (modelName == null || modelName.isBlank()) throw new IllegalArgumentException("modelName is required");
            return new AiTelemetryRecord(this);
        }
    }

    @Override
    public String toString() {
        return "AiTelemetryRecord{" +
                "id=" + id +
                ", requestId=" + requestId +
                ", fatherId=" + fatherId +
                ", modelName='" + modelName + '\'' +
                ", inputTokens=" + inputTokens +
                ", outputTokens=" + outputTokens +
                ", totalLatencyMs=" + totalLatencyMs +
                ", validationPassed=" + validationPassed +
                '}';
    }
}
