package com.dadcoach.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * D-043 (Unified Workflow 6.0b, risks R6/R18): which thread runs each {@code @Scheduled} job.
 *
 * <p>Before: Spring Boot's single default scheduler thread ({@code scheduling-1}) ran every job, so a belt promotion run
 * (shared-number gate 3 s + Meta 10 s per send, image + text: about 26 s per father) held up the receipt retry, the
 * template refresh, the idempotency purge, the platform deletions and the keep-warm ping.
 *
 * <p>Switch {@code app.scheduling.lanes} ({@code APP_SCHEDULING_LANES}, default {@code false}; on only for
 * {@code true} in any case - anything else, e.g. {@code yes}, {@code 1} or empty, is off, Big Boss's D-195 rule):
 * <ul>
 *   <li><b>off</b> - exactly as before: one scheduler, built by Boot's own builder (pool size 1, threads
 *       {@code scheduling-N}, the {@code spring.task.scheduling.*} settings), under all three names below.</li>
 *   <li><b>on</b> - two schedulers. The <b>messaging lane</b> ({@value #MESSAGING}, also the default
 *       {@code taskScheduler}) keeps one thread named {@code scheduling-1}: every job that sends WhatsApp, and every job
 *       nobody moved, runs there serially as before. The <b>housekeeping lane</b> ({@value #HOUSEKEEPING}, pool of
 *       {@value #HOUSEKEEPING_THREADS}, threads {@code scheduling-housekeeping-N}) takes only the jobs that opt in with
 *       {@code @Scheduled(scheduler = SchedulingLanes.HOUSEKEEPING)} - never one that sends.</li>
 * </ul>
 * The default is the serial lane on purpose: a new job runs where every job ran before until someone checks it and
 * moves it. A single {@code @Scheduled} method never overlaps itself in either mode (one future per method), so the
 * pool only lets <em>different</em> housekeeping jobs run side by side. The job-to-lane table and the cross-lane checks
 * are in DECISIONS D-043; {@code SchedulingLanesTest} fails when a new job is not in that table.
 *
 * <p>Declaring a scheduler bean makes Boot drop its own {@code taskScheduler} and - in Boot 3.4, whose
 * {@code applicationTaskExecutor} backs off on any {@link java.util.concurrent.Executor} bean - its application task
 * executor too. That executor is declared here as Boot declares it (lazy, same builder, same names), so the context
 * has the same executors as before in both modes.
 */
@Configuration(proxyBeanMethods = false)
public class SchedulingLanes {

    /** The serial lane: jobs that send WhatsApp (and any job not moved). Also the default scheduler. */
    public static final String MESSAGING = "messagingScheduler";
    /** The housekeeping lane: jobs that send nothing and were checked against the messaging jobs (D-043). */
    public static final String HOUSEKEEPING = "housekeepingScheduler";
    /** Spring's default scheduler name (used by every {@code @Scheduled} without a {@code scheduler}). */
    static final String DEFAULT = "taskScheduler";
    static final int HOUSEKEEPING_THREADS = 3;
    static final String HOUSEKEEPING_PREFIX = "scheduling-housekeeping-";
    static final String PROPERTY = "app.scheduling.lanes";

    /** Boot's application task executor, exactly as Boot 3.4 creates it (it would back off on the schedulers). */
    @Lazy
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }

    /** Switch off (anything but "true"): today's single scheduler thread runs every job, whatever lane it names. */
    @Configuration(proxyBeanMethods = false)
    @Conditional(LanesOff.class)
    static class OneLane {

        @Bean(name = {DEFAULT, MESSAGING, HOUSEKEEPING})
        ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
            return builder.build();
        }
    }

    /** Switch on: the sending jobs stay on one thread; the checked housekeeping jobs get a small pool. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = PROPERTY, havingValue = "true")
    static class TwoLanes {

        @Bean(name = {DEFAULT, MESSAGING})
        ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
            return builder.poolSize(1).build();
        }

        @Bean(name = HOUSEKEEPING)
        ThreadPoolTaskScheduler housekeepingScheduler(ThreadPoolTaskSchedulerBuilder builder) {
            return builder.poolSize(HOUSEKEEPING_THREADS).threadNamePrefix(HOUSEKEEPING_PREFIX).build();
        }
    }

    /**
     * "Not true": a mistyped value ({@code yes}, {@code on}, {@code 1}, empty) falls back to today's single scheduler
     * rather than to no scheduler bean at all (which would stop the application from starting).
     */
    static class LanesOff extends NoneNestedConditions {

        LanesOff() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @ConditionalOnProperty(name = PROPERTY, havingValue = "true")
        static class LanesOn {
        }
    }
}
