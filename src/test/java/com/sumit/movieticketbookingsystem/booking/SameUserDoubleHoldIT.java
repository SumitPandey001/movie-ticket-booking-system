package com.sumit.movieticketbookingsystem.booking;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService.CreateHold;
import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A double-click that sends two holds at once still ends in one booking; the other request is told a hold exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SameUserDoubleHoldIT {

    private static final int ROUNDS = 20;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private HoldService holdService;

    @Test
    void parallelHoldsBySameCustomerMakeOneBooking() throws Exception {
        BookableShow show = new BookingFixtures(mvc, jdbc).openShow();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int round = 0; round < ROUNDS; round++) {
                UUID customer = UUID.randomUUID();
                CyclicBarrier together = new CyclicBarrier(2);
                Future<Object> first = pool.submit(attempt(customer, show.id(), show.seats("A4"), together));
                Future<Object> second = pool.submit(attempt(customer, show.id(), show.seats("A5"), together));
                List<Object> outcomes = List.of(first.get(), second.get());

                List<Booking> bookings = outcomes.stream().filter(Booking.class::isInstance).map(Booking.class::cast)
                        .toList();
                assertThat(bookings).as("round %d", round).hasSize(1);
                assertThat(outcomes).as("round %d", round).contains(ErrorCode.ACTIVE_HOLD_EXISTS);
                holdService.release(bookings.getFirst().getId(), customer);
            }
        }
    }

    /** Returns the booking, or the error code it failed with. */
    private Callable<Object> attempt(UUID customer, long showId, Set<Long> seats, CyclicBarrier together) {
        return () -> {
            together.await();
            try {
                return holdService.createHold(new CreateHold(customer, showId, seats, null));
            } catch (DomainException e) {
                return e.code();
            }
        };
    }
}
