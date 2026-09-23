package com.sumit.movieticketbookingsystem.shared.job;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Two instances' schedulers firing the same job at once: the lock row in the database lets only one in. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, ShedLockIT.SlowJobConfig.class})
class ShedLockIT {

    @Autowired
    private SlowJob job;

    @Test
    void onlyOneOfTwoSimultaneousRunsGoesAhead() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService schedulers = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                schedulers.submit(() -> {
                    start.await();
                    job.run();
                    return null;
                });
            }
            start.countDown();
        }
        assertThat(job.runs()).isEqualTo(1);

        job.run();                                                  // the lock is free again afterwards
        assertThat(job.runs()).isEqualTo(2);
    }

    // read through a method: the bean is a CGLIB proxy, and a proxy's own fields are never set
    static class SlowJob {

        private final AtomicInteger runs = new AtomicInteger();

        public int runs() {
            return runs.get();
        }

        @SchedulerLock(name = "shedLockIT", lockAtMostFor = "PT10S")
        public void run() throws InterruptedException {
            runs.incrementAndGet();
            Thread.sleep(500);                                      // long enough for the other thread to try
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SlowJobConfig {

        @Bean
        SlowJob slowJob() {
            return new SlowJob();
        }
    }
}
