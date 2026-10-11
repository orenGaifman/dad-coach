package com.dadcoach.platformjobs;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unified Workflow 5.4 v3: the platform's call of one slot of a Dad Coach job - {@code POST /api/integration/jobs/{jobKey}}
 * {@code {jobKey, slot, attempt, runId}}, behind the scheduled-response callback key (the platform calls the same route).
 * 200 DONE / DUPLICATE / PRODUCT_OWNED, 409 BUSY (the job is running - asked again shortly), 404 unknown job, 400 no
 * slot, 500 the job failed (the platform retries).
 */
@RestController
public class PlatformJobsController {

    public record Call(String jobKey, String slot, Integer attempt, String runId) {}

    private final PlatformJobs jobs;

    public PlatformJobsController(PlatformJobs jobs) {
        this.jobs = jobs;
    }

    @PostMapping("/api/integration/jobs/{jobKey}")
    public ResponseEntity<Map<String, Object>> run(@PathVariable String jobKey, @RequestBody(required = false) Call call) {
        Instant slot;
        try {
            slot = Instant.parse(call == null ? "" : call.slot());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "BAD_REQUEST", "error", "slot is required (ISO-8601)"));
        }
        PlatformJobs.Outcome outcome = jobs.runForPlatform(jobKey, slot);
        return ResponseEntity.status(outcome.httpStatus()).body(outcome.body());
    }
}
