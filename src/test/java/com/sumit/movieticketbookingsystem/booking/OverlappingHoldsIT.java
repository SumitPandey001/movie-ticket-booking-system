package com.sumit.movieticketbookingsystem.booking;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two customers keep going for seat sets that share one seat. Rows are locked in seat order and never waited on,
 * so this can't deadlock, and the shared seat is never handed out twice.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OverlappingHoldsIT {

    private static final int ROUNDS = 200;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private HoldService holdService;

    @Test
    void overlappingHoldsNeverDeadlockOrShareASeat() throws Exception {
        BookableShow show = new BookingFixtures(mvc, jdbc).openShow();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Set<Long> aliceSeats = show.seats("A1", "A2");
        Set<Long> bobSeats = show.seats("A2", "A3");
        CyclicBarrier together = new CyclicBarrier(2);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int round = 0; round < ROUNDS; round++) {
                Future<Optional<Booking>> a = pool.submit(attempt(alice, show.id(), aliceSeats, together));
                Future<Optional<Booking>> b = pool.submit(attempt(bob, show.id(), bobSeats, together));
                List<Booking> winners = List.of(a.get(), b.get()).stream().flatMap(Optional::stream).toList();

                assertThat(winners).as("round %d", round).hasSizeLessThanOrEqualTo(1);
                winners.forEach(booking -> holdService.release(booking.getId(), booking.getUserId()));
            }
        }
        assertThat(jdbc.sql("SELECT count(*) FROM show_seat WHERE show_id = ? AND status = 'HELD'")
                .param(show.id()).query(Long.class).single()).isZero();
    }

    // Anything but a clean win or SeatsUnavailableException (a deadlock would surface as 40P01) fails the test.
    private Callable<Optional<Booking>> attempt(UUID customer, long showId, Set<Long> seats, CyclicBarrier together) {
        return () -> {
            together.await();
            try {
                return Optional.of(holdService.createHold(new CreateHold(customer, showId, seats, null)));
            } catch (SeatsUnavailableException e) {
                return Optional.empty();
            }
        };
    }
}
