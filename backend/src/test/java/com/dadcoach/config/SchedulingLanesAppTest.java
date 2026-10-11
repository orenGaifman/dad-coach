package com.dadcoach.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.AbstractIntegrationTest;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/**
 * D-043 (6.0b) in the real application context: the jobs Spring actually registered (the weekly belt job is switched
 * off in tests by its "-" cron; its lane is checked from its annotation in {@link SchedulingLanesTest}) and the
 * thread each one is routed to with the switch off (the shared test context); on: {@link SchedulingLanesOnAppTest}.
 */
class SchedulingLanesAppTest extends AbstractIntegrationTest {

    /** The registered jobs of the test context: everything but WeeklyGoalCompletionJob (cron "-"). */
    static final Map<String, String> REGISTERED = new TreeMap<>(SchedulingLanesTest.LANES);

    static {
        REGISTERED.remove("WeeklyGoalCompletionJob.run");
    }

    @Autowired
    ConfigurableApplicationContext context;

    @Test
    void switchOffEveryRegisteredJobRunsOnTodaysSingleThread() throws Exception {
        assertThat(context.getBeanNamesForType(TaskScheduler.class)).containsExactly("taskScheduler");
        assertThat(context.getBean("applicationTaskExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
        Map<String, String> threads = threadsOfRegisteredJobs(context);
        assertThat(threads.keySet()).isEqualTo(REGISTERED.keySet());
        threads.forEach((job, thread) -> assertThat(thread).as(job).isEqualTo(SchedulingLanesTest.TODAY));
    }

    /** "Class.method" -> the name of the thread a task with that job's qualifier runs on (Spring's routing). */
    static Map<String, String> threadsOfRegisteredJobs(ConfigurableApplicationContext context) throws Exception {
        Map<String, String> threads = new TreeMap<>();
        for (ScheduledTaskHolder holder : context.getBeansOfType(ScheduledTaskHolder.class).values()) {
            for (ScheduledTask task : holder.getScheduledTasks()) {
                String name = task.toString(); // com.dadcoach.x.Class.method
                String[] parts = name.split("\\.");
                String job = parts[parts.length - 2] + "." + parts[parts.length - 1];
                String lane = ((SchedulingAwareRunnable) task.getTask().getRunnable()).getQualifier();
                threads.put(job, SchedulingLanesTest.threadOf(context.getBeanFactory(), lane));
            }
        }
        return threads;
    }
}
