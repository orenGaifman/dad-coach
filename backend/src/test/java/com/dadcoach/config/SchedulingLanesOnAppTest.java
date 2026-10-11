package com.dadcoach.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * D-043 (6.0b): the real application starts with {@code APP_SCHEDULING_LANES=true} and routes every registered
 * housekeeping job to the housekeeping pool. Its own context, closed after the class (its jobs do not keep running
 * beside the shared test context).
 */
@TestPropertySource(properties = "app.scheduling.lanes=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchedulingLanesOnAppTest extends AbstractIntegrationTest {

    @Autowired
    ConfigurableApplicationContext context;

    @Test
    void switchOnTheHousekeepingJobsRunOnThePoolAndTheDefaultStaysTodaysThread() throws Exception {
        assertThat(context.getBean(SchedulingLanes.MESSAGING)).isSameAs(context.getBean("taskScheduler"));
        assertThat(context.getBean(SchedulingLanes.HOUSEKEEPING)).isNotSameAs(context.getBean("taskScheduler"));
        assertThat(context.getBean("applicationTaskExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
        Map<String, String> threads = SchedulingLanesAppTest.threadsOfRegisteredJobs(context);
        assertThat(threads.keySet()).isEqualTo(SchedulingLanesAppTest.REGISTERED.keySet());
        threads.forEach((job, thread) -> assertThat(thread).as(job).startsWith(SchedulingLanes.HOUSEKEEPING_PREFIX));
        assertThat(SchedulingLanesTest.threadOf(context.getBeanFactory(), SchedulingLanes.MESSAGING))
                .isEqualTo(SchedulingLanesTest.TODAY);
    }
}
