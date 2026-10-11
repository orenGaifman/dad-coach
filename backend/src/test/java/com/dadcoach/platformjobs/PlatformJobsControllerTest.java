package com.dadcoach.platformjobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.AbstractIntegrationTest;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unified Workflow 5.4 v3: the platform's call of a job slot through the real security chain - the scheduled-response
 * callback key is required, a slot is required, a PRODUCT job answers PRODUCT_OWNED, a PLATFORM job runs once per slot;
 * the QA probe's ops endpoint is closed without the ops key.
 */
@DisplayName("5.4 v3: job endpoint")
class PlatformJobsControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired PlatformJobs jobs;

    @AfterEach
    void reset() {
        jobs.setPlatformOwned(Set.of());
    }

    private static String call(String slot) {
        return "{\"jobKey\":\"qa_probe\",\"slot\":" + (slot == null ? "null" : "\"" + slot + "\"") + ",\"attempt\":1,\"runId\":42}";
    }

    @Test
    @DisplayName("no key or a wrong key is refused; a missing or bad slot is 400")
    void securityAndValidation() throws Exception {
        mvc.perform(post("/api/integration/jobs/qa_probe").contentType(MediaType.APPLICATION_JSON).content(call("2026-11-03T10:00:00Z")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
        mvc.perform(post("/api/integration/jobs/qa_probe").header("X-API-Key", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(call("2026-11-03T10:00:00Z")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
        mvc.perform(post("/api/integration/jobs/qa_probe").header("X-API-Key", CALLBACK_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(call(null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/integration/jobs/qa_probe").header("X-API-Key", CALLBACK_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(call("tomorrow")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PRODUCT_OWNED while the product owns it; DONE then DUPLICATE once the platform does; unknown job 404")
    void runsThroughTheChain() throws Exception {
        String slot = "2026-11-03T11:" + String.format("%02d", (int) Math.floorMod(System.nanoTime(), 60L)) + ":00Z";
        mvc.perform(post("/api/integration/jobs/qa_probe").header("X-API-Key", CALLBACK_KEY)
                        .header("X-Idempotency-Key", "job:qa_probe:" + slot).contentType(MediaType.APPLICATION_JSON).content(call(slot)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PRODUCT_OWNED"));
        jobs.setPlatformOwned(Set.of(QaProbe.KEY));
        mvc.perform(post("/api/integration/jobs/qa_probe").header("X-API-Key", CALLBACK_KEY)
                        .header("X-Idempotency-Key", "job:qa_probe:" + slot).contentType(MediaType.APPLICATION_JSON).content(call(slot)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(post("/api/integration/jobs/qa_probe").header("X-API-Key", CALLBACK_KEY)
                        .header("X-Idempotency-Key", "job:qa_probe:" + slot).contentType(MediaType.APPLICATION_JSON).content(call(slot)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DUPLICATE"));
        mvc.perform(post("/api/integration/jobs/no_such_job").header("X-API-Key", CALLBACK_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(call(slot)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the QA probe's ops endpoint is closed without the ops key")
    void opsClosed() throws Exception {
        mvc.perform(post("/api/ops/platform-jobs/qa-probe").contentType(MediaType.APPLICATION_JSON).content("{\"sleepMs\":0,\"failNext\":0}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403, 404));
    }
}
