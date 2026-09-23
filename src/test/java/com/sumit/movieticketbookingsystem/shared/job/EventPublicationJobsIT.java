package com.sumit.movieticketbookingsystem.shared.job;

import com.sumit.movieticketbookingsystem.Eventually;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfiguration.class, EventPublicationJobsIT.FlakyListenerConfig.class})
class EventPublicationJobsIT {

    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    private EventPublicationJobs jobs;

    @Autowired
    private FlakyListener listener;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void aFailedDeliveryIsRetriedOnceItsAMinuteOld() throws Exception {
        Ping ping = new Ping(UUID.randomUUID(), 1);
        publish(ping);
        Eventually.until("the first, failing delivery", WAIT, () -> listener.calls(ping) == 1);

        jobs.retryFailed();                                         // too recent: might still be in flight
        Thread.sleep(300);
        assertThat(listener.calls(ping)).isEqualTo(1);

        makeOlder(ping, "2 minutes");
        jobs.retryFailed();
        Eventually.until("the retry to complete it", WAIT, () -> completed(ping));
        assertThat(listener.calls(ping)).isEqualTo(2);
    }

    @Test
    void aDeliveryThatKeepsFailingIsEventuallyLeftAlone() throws Exception {
        Ping ping = new Ping(UUID.randomUUID(), Integer.MAX_VALUE);
        publish(ping);
        Eventually.until("the first delivery", WAIT, () -> listener.calls(ping) == 1);
        makeOlder(ping, "2 minutes");
        jdbc.sql("UPDATE event_publication SET completion_attempts = 10 WHERE serialized_event LIKE ?")
                .param("%" + ping.id() + "%").update();

        jobs.retryFailed();
        Thread.sleep(300);

        assertThat(listener.calls(ping)).isEqualTo(1);
    }

    @Test
    void deliveredEventsAreClearedAfterAWeek() {
        Ping old = new Ping(UUID.randomUUID(), 0);
        Ping recent = new Ping(UUID.randomUUID(), 0);
        publish(old);
        publish(recent);
        Eventually.until("both deliveries", WAIT, () -> completed(old) && completed(recent));
        makeOlder(old, "8 days");

        jobs.deleteOldCompleted();

        assertThat(rows(old)).isZero();
        assertThat(rows(recent)).isEqualTo(1);
    }

    private void publish(Ping ping) {
        tx.executeWithoutResult(status -> events.publishEvent(ping));
    }

    private void makeOlder(Ping ping, String interval) {
        jdbc.sql("""
                        UPDATE event_publication
                        SET publication_date = publication_date - CAST(? AS interval),
                            completion_date = completion_date - CAST(? AS interval)
                        WHERE serialized_event LIKE ?""")
                .params(interval, interval, "%" + ping.id() + "%").update();
    }

    private boolean completed(Ping ping) {
        return jdbc.sql("SELECT count(*) FROM event_publication WHERE completion_date IS NOT NULL "
                        + "AND serialized_event LIKE ?")
                .param("%" + ping.id() + "%").query(Long.class).single() == 1;
    }

    private long rows(Ping ping) {
        return jdbc.sql("SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?")
                .param("%" + ping.id() + "%").query(Long.class).single();
    }

    /** @param failures how many deliveries fail before one succeeds */
    record Ping(UUID id, int failures) {
    }

    // counters read through methods: the listener is an async proxy
    static class FlakyListener {

        private final Map<UUID, AtomicInteger> calls = new ConcurrentHashMap<>();

        @ApplicationModuleListener
        public void on(Ping ping) {
            int call = calls.computeIfAbsent(ping.id(), id -> new AtomicInteger()).incrementAndGet();
            if (call <= ping.failures()) {
                throw new IllegalStateException("Delivery " + call + " of " + ping.id() + " fails on purpose");
            }
        }

        public int calls(Ping ping) {
            return calls.getOrDefault(ping.id(), new AtomicInteger()).get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FlakyListenerConfig {

        @Bean
        FlakyListener flakyListener() {
            return new FlakyListener();
        }
    }
}
