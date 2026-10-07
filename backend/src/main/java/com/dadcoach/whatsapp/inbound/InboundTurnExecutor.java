package com.dadcoach.whatsapp.inbound;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Meta gets its 200 before the AI turn runs (playbook §34): turns run on a small pool, strictly one at a time per
 * sender, in arrival order - two quick messages from one father never race each other. With
 * {@code dad-coach.whatsapp.inbound-sync=true} (tests) a turn runs inline on the webhook thread.
 */
@Component
public class InboundTurnExecutor {

    private static final Logger log = LoggerFactory.getLogger(InboundTurnExecutor.class);

    private final ExecutorService pool = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "wa-turn");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();
    private final boolean inline;

    public InboundTurnExecutor(@Value("${dad-coach.whatsapp.inbound-sync:false}") boolean inline) {
        this.inline = inline;
    }

    public void submit(String sender, Runnable turn) {
        if (inline) {
            run(turn);
            return;
        }
        CompletableFuture<Void> next = tails.compute(sender, (key, tail) -> {
            CompletableFuture<Void> previous = tail == null ? CompletableFuture.completedFuture(null) : tail;
            return previous.handle((ok, error) -> null).thenRunAsync(() -> run(turn), pool);
        });
        // the sender's chain is forgotten once its last turn is done (no map entry per number forever)
        next.whenComplete((ok, error) -> tails.remove(sender, next));
    }

    private static void run(Runnable turn) {
        try {
            turn.run();
        } catch (RuntimeException e) {
            log.atError().setMessage("whatsapp.turn.failed").setCause(e).log();
        }
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }
}
