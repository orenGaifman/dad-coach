package com.dadcoach.platformjobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.client.PlatformPublisher;
import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * At start (off the main thread) the product registers with the platform through the shared client
 * ({@link PlatformPublisher}, platform P2_EXTENSIBILITY_SPEC §2-§3): its catalog ({@code platform-catalog.json}: HTTP
 * tools, context providers, kind→template mappings) when {@code app.platform-catalog.publish} is {@code dry-run} (report
 * only) or {@code apply}, then its jobs - the full list, each enabled when the platform owns it. Idempotent; a failure is
 * retried a few times and then logged (a job the platform never calls is run by the watchdog - a degraded mode).
 */
@Component
public class PlatformJobsSync {

    static final String CATALOG = "/platform-catalog.json";

    private final PlatformJobs jobs;
    private final WorkflowPlatformProperties platform;
    private final ObjectMapper json;
    private final String workerKey;
    private final boolean enabled;
    private final String catalogMode;

    @Autowired
    public PlatformJobsSync(PlatformJobs jobs, WorkflowPlatformProperties platform, ObjectMapper json,
                            @Value("${workflow.platform.worker-key:dad_3}") String workerKey,
                            @Value("${app.platform-jobs.sync:true}") boolean enabled,
                            @Value("${app.platform-catalog.publish:off}") String catalogMode) {
        this.jobs = jobs;
        this.platform = platform;
        this.json = json;
        this.workerKey = workerKey;
        this.enabled = enabled;
        this.catalogMode = catalogMode;
    }

    /** Tests: jobs only. */
    PlatformJobsSync(PlatformJobs jobs, WorkflowPlatformProperties platform, ObjectMapper json, String workerKey, boolean enabled) {
        this(jobs, platform, json, workerKey, enabled, "off");
    }

    private PlatformPublisher publisher() {
        return new PlatformPublisher(platform.getBaseUrl(), platform.getApiKey(), json);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (enabled && platform.isEnabled() && publisher().configured()) {
            publisher().register(workerKey, catalogMode, () -> getClass().getResourceAsStream(CATALOG), this::jobsList);
        }
    }

    void syncWithRetries() {
        PlatformPublisher publisher = publisher();
        publisher.withRetries("jobs", () -> publisher.publishJobs(workerKey, jobsList()));
    }

    int sync() {
        return publisher().publishJobs(workerKey, jobsList()).status();
    }

    /** The registration body: every job, enabled when the platform owns it. */
    Map<String, Object> body() {
        return Map.of("workerKey", workerKey, "jobs", jobsList());
    }

    private List<Map<String, Object>> jobsList() {
        return jobs.jobs().stream().map(j -> PlatformPublisher.job(j.key(), j.every(), j.cron(), j.zone(), j.timeout(),
                jobs.platformOwns(j.key()), j.description())).toList();
    }
}
