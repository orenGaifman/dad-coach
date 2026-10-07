package com.dadcoach.whatsapp.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Meta's 200 never waits for the AI; one sender's turns run one at a time, in order; senders run in parallel. */
class InboundTurnExecutorTest {

    @Test
    void oneSendersTurnsNeverOverlapAndKeepTheirOrder() throws Exception {
        InboundTurnExecutor executor = new InboundTurnExecutor(false);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        List<Integer> order = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            int n = i;
            executor.submit("+19995550700", () -> {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                sleep(30);
                order.add(n);
                running.decrementAndGet();
                done.countDown();
            });
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(maxRunning.get()).isEqualTo(1);
        assertThat(order).containsExactly(0, 1, 2, 3, 4);
        executor.shutdown();
    }

    @Test
    void differentSendersRunAtTheSameTimeAndAFailedTurnDoesNotBlockTheNext() throws Exception {
        InboundTurnExecutor executor = new InboundTurnExecutor(false);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        for (String sender : new String[]{"+19995550701", "+19995550702"}) {
            executor.submit(sender, () -> {
                bothStarted.countDown();
                await(release);
            });
        }
        assertThat(bothStarted.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();

        CountDownLatch after = new CountDownLatch(1);
        executor.submit("+19995550703", () -> { throw new IllegalStateException("boom"); });
        executor.submit("+19995550703", after::countDown);
        assertThat(after.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
