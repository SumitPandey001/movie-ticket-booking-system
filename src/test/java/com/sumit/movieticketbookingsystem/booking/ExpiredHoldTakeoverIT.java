package com.sumit.movieticketbookingsystem.booking;

import com.sumit.movieticketbookingsystem.MutableClock;
import com.sumit.movieticketbookingsystem.MutableClockConfiguration;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingStatus;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService.CreateHold;
import com.sumit.movieticketbookingsystem.inventory.SeatAvailabilityReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * A hold that runs out frees its seats the moment someone asks for them; no sweeper has to run first.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class ExpiredHoldTakeoverIT {

    private static final Duration PAST_THE_HOLD = Duration.ofMinutes(9);    // holds last 8 minutes

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private HoldService holdService;

    @Autowired
    private SeatAvailabilityReader availability;

    @Autowired
    private MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void anotherCustomerTakesTheSeatsOfAnExpiredHold() throws Exception {
        BookableShow show = new BookingFixtures(mvc, jdbc).openShow();
        assertThat(seatsLeft(show)).isEqualTo(10);
        holdService.createHold(new CreateHold(UUID.randomUUID(), show.id(), show.seats("A1", "A2"), null));
        assertThat(seatsLeft(show)).isEqualTo(8);

        clock.advance(PAST_THE_HOLD);
        Booking takeover = holdService.createHold(new CreateHold(UUID.randomUUID(), show.id(), show.seats("A1"), null));

        assertThat(takeover.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(seatsLeft(show)).isEqualTo(8);    // A1 was already counted as gone, so no double decrement
        mvc.perform(asCustomer(get("/api/v1/shows/{id}/seats", show.id())))
                .andExpect(jsonPath("$.seats[?(@.label == 'A1')].status").value(contains("HELD")))
                .andExpect(jsonPath("$.seats[?(@.label == 'A2')].status").value(contains("AVAILABLE")));
    }

    @Test
    void customerCanHoldAgainOnceTheirOwnHoldRanOut() throws Exception {
        BookableShow show = new BookingFixtures(mvc, jdbc).openShow();
        UUID customer = UUID.randomUUID();
        Booking first = holdService.createHold(new CreateHold(customer, show.id(), show.seats("B1"), null));

        clock.advance(PAST_THE_HOLD);
        Booking second = holdService.createHold(new CreateHold(customer, show.id(), show.seats("B2"), null));

        assertThat(jdbc.sql("SELECT status FROM booking WHERE id = ?").param(first.getId()).query(String.class)
                .single()).isEqualTo("EXPIRED");
        assertThat(second.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(jdbc.sql("SELECT status FROM show_seat WHERE show_id = ? AND layout_seat_id = ?")
                .params(show.id(), show.seatIdsByLabel().get("B1")).query(String.class).single())
                .isEqualTo("AVAILABLE");
    }

    private int seatsLeft(BookableShow show) {
        return availability.seatsLeft(List.of(show.id())).get(show.id());
    }
}
