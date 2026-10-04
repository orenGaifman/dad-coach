package com.dadcoach.integration.platform.lifecycle;

import com.dadcoach.integration.platform.PlatformWorkflowConfig;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * The AI Workflow Platform's generic person lifecycle API ({@code /api/v1/tenancy/**}), with Dad Coach's own
 * integration key (bound to product dad_coach). Dad Coach is a single-tenant product: its one tenant is fixed
 * by the platform's configuration ({@code workflow.platform.tenant-id}).
 */
@Component
public class PlatformTenancyClient {

    private final WebClient webClient;
    private final PlatformWorkflowConfig config;

    public PlatformTenancyClient(PlatformWorkflowConfig config) {
        this.config = config;
        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-API-Key", config.getApiKey())
                .build();
    }

    public record PersonUpsert(String personRef, String externalUserId, String channel, String displayName, String status) {
    }

    public record PeopleSync(UUID tenantId, boolean dryRun, List<PersonUpsert> people) {
    }

    public record Conflict(String personRef, String idTail, String reason) {
    }

    public record PeopleResult(UUID tenantId, boolean dryRun, int created, int updated, int unchanged,
                               int linkedWorkerInstances, int linkedWorkflowInstances, List<Conflict> conflicts) {
    }

    public record PersonDeletionResult(UUID tenantId, String personRef, String outcome, boolean dryRun, int workflowInstances,
                                       int workerInstances, int messages, int agentExecutions, int scheduledTriggers,
                                       int conversationReviews, boolean profileDeleted) {
    }

    public UUID tenantId() {
        return config.getTenantId();
    }

    /** Registers (or updates) one person of Dad Coach's tenant under its ref - linking the conversations of its identity. */
    public PeopleResult upsertPerson(PersonUpsert person) {
        return webClient.put()
                .uri("/api/v1/tenancy/people")
                .bodyValue(new PeopleSync(config.getTenantId(), false, List.of(person)))
                .retrieve()
                .bodyToMono(PeopleResult.class)
                .timeout(timeout())
                .block();
    }

    /** Deletes everything the platform holds about the person (idempotent: ABSENT when nothing is there). */
    public PersonDeletionResult deletePerson(String personRef) {
        return webClient.delete()
                .uri("/api/v1/tenancy/tenants/{tenant}/people/{ref}", config.getTenantId(), personRef)
                .retrieve()
                .bodyToMono(PersonDeletionResult.class)
                .timeout(timeout())
                .block();
    }

    private Duration timeout() {
        return Duration.ofMillis(Math.max(config.getReadTimeoutMs(), 30_000));
    }
}
