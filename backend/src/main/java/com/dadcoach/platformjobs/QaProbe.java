package com.dadcoach.platformjobs;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Review J-7: a job with no business effect, for controlled production checks of the platform's scheduling (restart
 * mid-run, retries, duplicates): it sleeps {@code sleepMs} and fails the next {@code failNext} runs, both set through the
 * ops API ({@code POST /api/ops/platform-jobs/qa-probe}). Its key is {@code qa_probe}; it runs only when the platform
 * owns it (it has no product tick).
 */
public final class QaProbe {

    public static final String KEY = "qa_probe";
    private final AtomicLong sleepMs = new AtomicLong();
    private final AtomicInteger failNext = new AtomicInteger();
    private final AtomicInteger runs = new AtomicInteger();

    public void configure(long sleepMs, int failNext) {
        this.sleepMs.set(Math.max(0, Math.min(sleepMs, 600_000)));
        this.failNext.set(Math.max(0, Math.min(failNext, 10)));
    }

    public Map<String, Object> state() {
        return Map.of("sleepMs", sleepMs.get(), "failNext", failNext.get(), "runs", runs.get());
    }

    public Object run() throws Exception {
        if (failNext.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            throw new IllegalStateException("qa_probe: failing on purpose");
        }
        long sleep = sleepMs.get();
        if (sleep > 0) {
            Thread.sleep(sleep);
        }
        return Map.of("probe", "ok", "run", runs.incrementAndGet(), "at", Instant.now().toString());
    }
}
