package com.sumit.movieticketbookingsystem.booking;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService.CreateHold;
import com.sumit.movieticketbookingsystem.inventory.SeatsUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The core guarantee: however many customers go for the same seats at the same moment, exactly one gets them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SeatHoldConcurrencyIT {

    private static final int ATTEMPTS = 500;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private HoldService holdService;

    @Test
    void onlyOneOf500ConcurrentHoldsWins() throws Exception {
        BookableShow show = new BookingFixtures(mvc, jdbc).openShow();
        Set<Long> seats = show.seats("B3", "B4");
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < ATTEMPTS; i++) {
                UUID customer = UUID.randomUUID();
                pool.submit(() -> {
                    start.await();
                    try {
                        holdService.createHold(new CreateHold(customer, show.id(), seats));
                        wins.incrementAndGet();
                    } catch (SeatsUnavailableException e) {
                        conflicts.incrementAndGet();
                    } catch (RuntimeException e) {
                        unexpected.add(e);
                    }
                    return null;
                });
            }
            start.countDown();
        }                                       // close() waits for every task

        assertThat(unexpected).isEmpty();
        assertThat(wins).hasValue(1);
        assertThat(conflicts).hasValue(ATTEMPTS - 1);
        assertThat(jdbc.sql("SELECT count(DISTINCT booking_id) FROM show_seat WHERE show_id = ? AND status = 'HELD'")
                .param(show.id()).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("SELECT count(*) FROM booking WHERE show_id = ?")
                .param(show.id()).query(Long.class).single()).isEqualTo(1L);
    }
}
