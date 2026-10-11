package com.dadcoach.platformjobs;

import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Unified Workflow 5.4 v3: at start (off the main thread) Dad Coach registers its jobs with the platform -
 * {@code PUT /api/v1/worker/jobs}, the full list, each enabled only when the platform owns it ({@code DC_PLATFORM_JOBS}).
 * Idempotent; a failure is retried a few times and then logged (the jobs keep running on their PRODUCT ticks, and a
 * PLATFORM job the platform never calls is run by the watchdog).
 */
@Component
public class PlatformJobsSync {

    private static final Logger log = LoggerFactory.getLogger(PlatformJobsSync.class);

    private final PlatformJobs jobs;
    private final WorkflowPlatformProperties platform;
    private final ObjectMapper json;
    private final String workerKey;
    private final boolean enabled;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public PlatformJobsSync(PlatformJobs jobs, WorkflowPlatformProperties platform, ObjectMapper json,
                            @Value("${workflow.platform.worker-key:dad_3}") String workerKey,
                            @Value("${app.platform-jobs.sync:true}") boolean enabled) {
        this.jobs = jobs;
        this.platform = platform;
        this.json = json;
        this.workerKey = workerKey;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled || !platform.isEnabled() || platform.getBaseUrl() == null || platform.getBaseUrl().isBlank()) {
            return;
        }
        Thread t = new Thread(this::syncWithRetries, "platform-jobs-sync");
        t.setDaemon(true);
        t.start();
    }

    void syncWithRetries() {
        long[] waits = {0, 30, 120, 600};
        for (long wait : waits) {
            try {
                Thread.sleep(wait * 1000);
                int status = sync();
                if (status >= 200 && status < 300) {
                    return;
                }
                if (status == 404) {
                    log.atInfo().log("platform_jobs.sync_unsupported - the platform has no job API yet");
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.atWarn().addKeyValue("error", e.getClass().getSimpleName()).log("platform_jobs.sync_failed");
            }
        }
        log.atWarn().log("platform_jobs.sync_gave_up");
    }

    /** The registration body: every job, enabled when the platform owns it. */
    Map<String, Object> body() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PlatformJobs.Job job : jobs.jobs()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("jobKey", job.key());
            if (job.every() != null) {
                m.put("every", job.every().toString());
            } else {
                m.put("cron", job.cron());
                m.put("timezone", job.zone());
            }
            m.put("timeout", job.timeout().toString());
            m.put("enabled", jobs.platformOwns(job.key()));
            m.put("description", job.description());
            list.add(m);
        }
        return Map.of("workerKey", workerKey, "jobs", list);
    }

    int sync() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(platform.getBaseUrl().replaceAll("/+$", "") + "/api/v1/worker/jobs"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("X-API-Key", platform.getApiKey() == null ? "" : platform.getApiKey())
                .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body())))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        log.atInfo().addKeyValue("status", response.statusCode()).addKeyValue("jobs", jobs.jobs().size())
                .addKeyValue("platformOwned", jobs.jobs().stream().filter(j -> jobs.platformOwns(j.key())).map(PlatformJobs.Job::key).toList())
                .log("platform_jobs.synced");
        return response.statusCode();
    }
}
