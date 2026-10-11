package com.dadcoach.config;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.TaskSchedulerRouter;

/**
 * D-043 (6.0b): the scheduler lanes without a database - Boot's own task auto-configuration, {@link SchedulingLanes}
 * and {@code @EnableScheduling}/{@code @EnableAsync} as in {@code DadCoachApplication}. Covers: every real job is in
 * the lane table; the thread each real job lands on, switch off and on; switch off = Boot's scheduler and executor as
 * before; with the switch on a housekeeping job runs while a belt send waits on a slow Meta, and the messaging jobs
 * never overlap.
 */
class SchedulingLanesTest {

    static final String TODAY = "scheduling-1";

    /** The lane table of D-043 ("" = no scheduler named = the default, i.e. the messaging lane). */
    static final Map<String, String> LANES = Map.of(
            "WeeklyGoalCompletionJob.run", SchedulingLanes.MESSAGING,
            "DeliveryReceipts.retryPending", SchedulingLanes.HOUSEKEEPING,
            "MetaTemplateDirectory.scheduled", SchedulingLanes.HOUSEKEEPING,
            "IdempotencyService.purgeExpired", SchedulingLanes.HOUSEKEEPING,
            "PlatformPersonDeletions.sendPeriodically", SchedulingLanes.HOUSEKEEPING,
            "KeepWarmJob.keepWarm", SchedulingLanes.HOUSEKEEPING);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class,
                    TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(AppLike.class, SchedulingLanes.class);

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @EnableAsync
    static class AppLike {
    }

    @Test
    void everyScheduledJobInTheProductIsInTheLaneTable() throws Exception {
        assertThat(productJobs()).as("a new @Scheduled job needs a lane decision (D-043) and a row in LANES")
                .isEqualTo(new TreeMap<>(LANES));
    }

    @Test
    void switchOffEveryJobRunsOnTodaysSingleThread() throws Exception {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            for (Map.Entry<String, String> job : productJobs().entrySet()) {
                assertThat(threadOf(context, job.getValue())).as(job.getKey()).isEqualTo(TODAY);
            }
        });
    }

    @Test
    void switchOnSendingJobsKeepTheSingleThreadAndHousekeepingGetsThePool() throws Exception {
        runner.withPropertyValues("app.scheduling.lanes=true").run(context -> {
            assertThat(context).hasNotFailed();
            for (Map.Entry<String, String> job : productJobs().entrySet()) {
                String thread = threadOf(context, job.getValue());
                if (SchedulingLanes.HOUSEKEEPING.equals(job.getValue())) {
                    assertThat(thread).as(job.getKey()).startsWith(SchedulingLanes.HOUSEKEEPING_PREFIX);
                } else {
                    assertThat(thread).as(job.getKey()).isEqualTo(TODAY);
                }
            }
            assertThat(threadOf(context, "")).as("a job that names no lane").isEqualTo(TODAY);
            ThreadPoolTaskScheduler housekeeping = context.getBean(SchedulingLanes.HOUSEKEEPING, ThreadPoolTaskScheduler.class);
            assertThat(housekeeping.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(3);
            assertThat(context.getBean(SchedulingLanes.MESSAGING, ThreadPoolTaskScheduler.class)
                    .getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
            assertThat(context.getBean(SchedulingLanes.MESSAGING)).isSameAs(context.getBean("taskScheduler"));
            assertThat(housekeeping).isNotSameAs(context.getBean("taskScheduler"));
        });
    }

    @Test
    void switchOffHasBootsSchedulerAndExecutorExactlyAsBefore() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class,
                        TaskExecutionAutoConfiguration.class))
                .withUserConfiguration(AppLike.class)
                .run(boot -> runner.withPropertyValues("app.scheduling.lanes=false").run(ours -> {
                    assertThat(ours.getBeanNamesForType(TaskScheduler.class)).containsExactly("taskScheduler");
                    assertThat(Set.of(ours.getBeanFactory().getAliases("taskScheduler")))
                            .containsExactlyInAnyOrder(SchedulingLanes.MESSAGING, SchedulingLanes.HOUSEKEEPING);
                    assertThat(sorted(ours.getBeanNamesForType(Executor.class)))
                            .isEqualTo(sorted(boot.getBeanNamesForType(Executor.class)));
                    assertThat(Set.of(ours.getBeanFactory().getAliases("applicationTaskExecutor")))
                            .isEqualTo(Set.of(boot.getBeanFactory().getAliases("applicationTaskExecutor")));

                    ThreadPoolTaskScheduler bootScheduler = boot.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
                    ThreadPoolTaskScheduler ourScheduler = ours.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
                    assertThat(ourScheduler.getScheduledThreadPoolExecutor().getCorePoolSize())
                            .isEqualTo(bootScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
                    assertThat(ourScheduler.getThreadNamePrefix()).isEqualTo(bootScheduler.getThreadNamePrefix())
                            .isEqualTo("scheduling-");
                    assertThat(ourScheduler.isDaemon()).isEqualTo(bootScheduler.isDaemon());

                    BeanDefinition bootExecutorDef = boot.getBeanFactory().getBeanDefinition("applicationTaskExecutor");
                    BeanDefinition ourExecutorDef = ours.getBeanFactory().getBeanDefinition("applicationTaskExecutor");
                    assertThat(ourExecutorDef.isLazyInit()).isEqualTo(bootExecutorDef.isLazyInit()).isTrue();
                    ThreadPoolTaskExecutor bootExecutor = boot.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class);
                    ThreadPoolTaskExecutor ourExecutor = ours.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class);
                    assertThat(ourExecutor.getCorePoolSize()).isEqualTo(bootExecutor.getCorePoolSize());
                    assertThat(ourExecutor.getMaxPoolSize()).isEqualTo(bootExecutor.getMaxPoolSize());
                    assertThat(ourExecutor.getQueueCapacity()).isEqualTo(bootExecutor.getQueueCapacity());
                    assertThat(ourExecutor.getKeepAliveSeconds()).isEqualTo(bootExecutor.getKeepAliveSeconds());
                    assertThat(ourExecutor.getThreadNamePrefix()).isEqualTo(bootExecutor.getThreadNamePrefix());
                }));
    }

    @Test
    void switchOnAHousekeepingJobRunsWhileABeltSendWaitsOnMeta() {
        runner.withPropertyValues("app.scheduling.lanes=true").withUserConfiguration(SlowMeta.class).run(context -> {
            Jobs jobs = context.getBean(Jobs.class);
            try {
                assertThat(jobs.sendStarted.await(5, SECONDS)).as("the belt job reached Meta").isTrue();
                int housekeepingAtBlock = jobs.housekeepingRuns.get();
                int unmovedAtBlock = jobs.unmovedRuns.get();
                assertThat(eventually(() -> jobs.housekeepingRuns.get() >= housekeepingAtBlock + 3))
                        .as("housekeeping keeps running while Meta is slow").isTrue();
                assertThat(jobs.unmovedRuns.get()).as("the other messaging-lane job waits its turn").isEqualTo(unmovedAtBlock);
                assertThat(jobs.maxActive.get()).isEqualTo(1);
            } finally {
                jobs.metaAnswers.countDown();
            }
            assertThat(eventually(() -> jobs.unmovedRuns.get() > 0)).isTrue();
            assertThat(jobs.threads.get("belt")).containsExactly(TODAY);
            assertThat(jobs.threads.get("unmoved")).containsExactly(TODAY);
            assertThat(jobs.threads.get("housekeeping")).allMatch(t -> t.startsWith(SchedulingLanes.HOUSEKEEPING_PREFIX));
        });
    }

    @Test
    void switchOffEverythingWaitsBehindABeltSendAsToday() {
        runner.withUserConfiguration(SlowMeta.class).run(context -> {
            Jobs jobs = context.getBean(Jobs.class);
            try {
                assertThat(jobs.sendStarted.await(5, SECONDS)).isTrue();
                int housekeepingAtBlock = jobs.housekeepingRuns.get();
                Thread.sleep(300);
                assertThat(jobs.housekeepingRuns.get()).as("one thread: housekeeping waits for Meta").isEqualTo(housekeepingAtBlock);
            } finally {
                jobs.metaAnswers.countDown();
            }
            assertThat(eventually(() -> jobs.housekeepingRuns.get() > 0)).isTrue();
            jobs.threads.values().forEach(threads -> assertThat(threads).containsExactly(TODAY));
        });
    }

    @Test
    void switchOnTheMessagingJobsNeverRunAtTheSameTime() {
        runner.withPropertyValues("app.scheduling.lanes=true").withUserConfiguration(BusyMeta.class).run(context -> {
            Jobs jobs = context.getBean(Jobs.class);
            assertThat(eventually(() -> jobs.beltRuns.get() >= 20 && jobs.unmovedRuns.get() >= 20
                    && jobs.housekeepingRuns.get() >= 5)).isTrue();
            assertThat(jobs.maxActive.get()).as("messaging-lane jobs at once").isEqualTo(1);
            assertThat(jobs.threads.get("belt")).containsExactly(TODAY);
            assertThat(jobs.threads.get("unmoved")).containsExactly(TODAY);
        });
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** Two messaging-lane jobs (one named, one that names no lane) and a housekeeping job; Meta answers when told. */
    static class Jobs {
        final boolean slowFirstSend;
        final CountDownLatch sendStarted = new CountDownLatch(1);
        final CountDownLatch metaAnswers = new CountDownLatch(1);
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maxActive = new AtomicInteger();
        final AtomicInteger beltRuns = new AtomicInteger();
        final AtomicInteger unmovedRuns = new AtomicInteger();
        final AtomicInteger housekeepingRuns = new AtomicInteger();
        final Map<String, Set<String>> threads = new ConcurrentHashMap<>();

        Jobs(boolean slowFirstSend) {
            this.slowFirstSend = slowFirstSend;
        }

        @Scheduled(fixedDelay = 1, scheduler = SchedulingLanes.MESSAGING)
        void beltPromotions() throws InterruptedException {
            enter("belt");
            try {
                beltRuns.incrementAndGet();
                if (slowFirstSend && sendStarted.getCount() > 0) {
                    sendStarted.countDown();
                    metaAnswers.await(10, SECONDS); // the fake slow Meta
                } else {
                    Thread.sleep(2);
                }
            } finally {
                active.decrementAndGet();
            }
        }

        @Scheduled(fixedDelay = 1)
        void unmovedJob() throws InterruptedException {
            enter("unmoved");
            try {
                unmovedRuns.incrementAndGet();
                Thread.sleep(2);
            } finally {
                active.decrementAndGet();
            }
        }

        @Scheduled(fixedDelay = 5, scheduler = SchedulingLanes.HOUSEKEEPING)
        void housekeeping() {
            threads.computeIfAbsent("housekeeping", k -> ConcurrentHashMap.newKeySet()).add(Thread.currentThread().getName());
            housekeepingRuns.incrementAndGet();
        }

        private void enter(String job) {
            threads.computeIfAbsent(job, k -> ConcurrentHashMap.newKeySet()).add(Thread.currentThread().getName());
            maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SlowMeta {
        @Bean
        Jobs jobs() {
            return new Jobs(true);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BusyMeta {
        @Bean
        Jobs jobs() {
            return new Jobs(false);
        }
    }

    /** Every {@code @Scheduled} method of the product (main classes only): "Class.method" -> the lane it names. */
    static Map<String, String> productJobs() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> reader.getAnnotationMetadata().hasAnnotatedMethods(Scheduled.class.getName())
                && !reader.getResource().getURL().toString().contains("/test-classes/"));
        Map<String, String> jobs = new TreeMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.dadcoach")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            Arrays.stream(type.getDeclaredMethods()).forEach(method -> AnnotatedElementUtils
                    .findMergedRepeatableAnnotations(method, Scheduled.class)
                    .forEach(s -> jobs.put(type.getSimpleName() + "." + method.getName(), s.scheduler())));
        }
        return jobs;
    }

    /** The thread a task naming this lane runs on, routed exactly as {@code @Scheduled} routes it (Spring's router). */
    static String threadOf(BeanFactory beans, String lane) throws Exception {
        TaskSchedulerRouter router = new TaskSchedulerRouter();
        router.setBeanFactory(beans);
        CompletableFuture<String> thread = new CompletableFuture<>();
        router.schedule(new SchedulingAwareRunnable() {
            @Override
            public void run() {
                thread.complete(Thread.currentThread().getName());
            }

            @Override
            public String getQualifier() {
                return lane == null || lane.isEmpty() ? null : lane;
            }
        }, Instant.now());
        try {
            return thread.get(30, SECONDS);
        } finally {
            router.destroy();
        }
    }

    static String threadOf(AssertableApplicationContext context, String lane) throws Exception {
        return threadOf(context.getBeanFactory(), lane);
    }

    private static boolean eventually(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    private static Set<String> sorted(String[] names) {
        return new TreeSet<>(Arrays.asList(names));
    }
}
