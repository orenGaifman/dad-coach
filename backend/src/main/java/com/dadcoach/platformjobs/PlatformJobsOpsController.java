package com.dadcoach.platformjobs;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unified Workflow 5.4 v3 (review J-7): the operator's handle on the {@code qa_probe} job (behind the ops key) - how long
 * its next runs sleep and how many of them fail on purpose, for controlled production checks of restarts and retries.
 * It touches no business data.
 */
@RestController
public class PlatformJobsOpsController {

    public record ProbeConfig(Long sleepMs, Integer failNext) {}

    private final PlatformJobs jobs;

    public PlatformJobsOpsController(PlatformJobs jobs) {
        this.jobs = jobs;
    }

    @PostMapping("/api/ops/platform-jobs/qa-probe")
    public ResponseEntity<Map<String, Object>> configure(@RequestBody ProbeConfig config) {
        QaProbe probe = jobs.probe().orElseThrow();
        probe.configure(config.sleepMs() == null ? 0 : config.sleepMs(), config.failNext() == null ? 0 : config.failNext());
        return ResponseEntity.ok(probe.state());
    }

    @GetMapping("/api/ops/platform-jobs/qa-probe")
    public ResponseEntity<Map<String, Object>> state() {
        return ResponseEntity.ok(jobs.probe().orElseThrow().state());
    }
}
