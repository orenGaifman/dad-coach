package com.dadcoach.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * D-043 (6.0b): the real application as production starts it - {@code -Dspring.main.lazy-initialization=true}
 * (Dockerfile) - with the switch off (unset): every registered job is on today's single thread. Its own context,
 * closed after the class.
 */
@TestPropertySource(properties = "spring.main.lazy-initialization=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchedulingLanesLazyAppTest extends AbstractIntegrationTest {

    @Autowired
    ConfigurableApplicationContext context;

    @Test
    void prodLazyInitializationWithTheSwitchOffKeepsEveryJobOnTodaysThread() throws Exception {
        assertThat(context.getBeanFactory().getBeanDefinition("taskScheduler").isLazyInit()).as("lazy as in prod").isTrue();
        assertThat(context.getBeanNamesForType(TaskScheduler.class)).containsExactly("taskScheduler");
        Map<String, String> threads = SchedulingLanesAppTest.threadsOfRegisteredJobs(context);
        assertThat(threads.keySet()).isEqualTo(SchedulingLanesAppTest.REGISTERED.keySet());
        threads.forEach((job, thread) -> assertThat(thread).as(job).isEqualTo(SchedulingLanesTest.TODAY));
    }
}
