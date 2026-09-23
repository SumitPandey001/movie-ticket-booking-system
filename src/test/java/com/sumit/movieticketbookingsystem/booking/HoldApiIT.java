package com.sumit.movieticketbookingsystem.booking;

import com.jayway.jsonpath.JsonPath;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.LongStream;

import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HoldApiIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private BookingFixtures fixtures;
    private long showId;
    private Map<String, Long> seats;     // A1-A5 regular (₹200), B1-B5 premium (₹300)

    @BeforeEach
    void openShow() throws Exception {
        fixtures = new BookingFixtures(mvc, jdbc);
        BookingFixtures.BookableShow show = fixtures.openShow();
        showId = show.id();
        seats = show.seatIdsByLabel();
    }

    @Test
    void holdFreezesThePriceAndTakesTheSeats() throws Exception {
        Instant before = Instant.now();
        String body = hold(UUID.randomUUID(), "A1", "B1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.bookingRef").value(matchesPattern("BK[0-9A-Z]{6}")))
                .andExpect(jsonPath("$.seats[*].label").value(contains("A1", "B1")))
                // A1: 20000 + fee 2000 + GST 3600 + fee GST 360;  B1: 30000 + 2000 + 5400 + 360
                .andExpect(jsonPath("$.seats[0].amountPaise").value(25960))
                .andExpect(jsonPath("$.seats[1].amountPaise").value(37760))
                .andExpect(jsonPath("$.price.subtotalPaise").value(50000))
                .andExpect(jsonPath("$.price.feePaise").value(4000))
                .andExpect(jsonPath("$.price.taxPaise").value(9720))
                .andExpect(jsonPath("$.price.totalPaise").value(63720))
                .andReturn().getResponse().getContentAsString();

        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.holdExpiresAt"));
        assertThat(Duration.between(before, expiresAt)).isBetween(Duration.ofMinutes(8), Duration.ofMinutes(9));

        mvc.perform(asCustomer(get("/api/v1/shows/{id}/seats", showId)))
                .andExpect(jsonPath("$.seats[?(@.label == 'A1')].status").value(contains("HELD")))
                .andExpect(jsonPath("$.seats[?(@.label == 'A2')].status").value(contains("AVAILABLE")));
    }

    @Test
    void secondCustomerCantHoldTheSameSeat() throws Exception {
        hold(UUID.randomUUID(), "A2", "A3").andExpect(status().isCreated());

        hold(UUID.randomUUID(), "A3", "A4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEATS_UNAVAILABLE"))
                .andExpect(jsonPath("$.unavailableSeatIds").value(contains(seats.get("A3").intValue())));
    }

    @Test
    void oneLiveHoldPerCustomerAndShow() throws Exception {
        UUID customer = UUID.randomUUID();
        String first = hold(customer, "B2").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        hold(customer, "B3")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_HOLD_EXISTS"))
                .andExpect(jsonPath("$.bookingId").value(JsonPath.<String>read(first, "$.bookingId")));
    }

    @Test
    void releasingFreesTheSeatsAndAllowsANewHold() throws Exception {
        UUID customer = UUID.randomUUID();
        String bookingId = JsonPath.read(hold(customer, "B4", "B5").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.bookingId");

        mvc.perform(asCustomer(post("/api/v1/bookings/{id}/release", bookingId), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        mvc.perform(asCustomer(post("/api/v1/bookings/{id}/release", bookingId), customer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        hold(UUID.randomUUID(), "B4").andExpect(status().isCreated());   // someone else gets the seat
        hold(customer, "B5").andExpect(status().isCreated());            // and the customer may hold again
    }

    @Test
    void bookingsAreOnlyVisibleToTheirOwner() throws Exception {
        UUID customer = UUID.randomUUID();
        String bookingId = JsonPath.read(hold(customer, "A5").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.bookingId");

        mvc.perform(asCustomer(get("/api/v1/bookings/{id}", bookingId), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[0].label").value("A5"));
        mvc.perform(asCustomer(get("/api/v1/bookings/{id}", bookingId)))
                .andExpect(status().isNotFound());
        mvc.perform(asCustomer(post("/api/v1/bookings/{id}/release", bookingId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsHoldsThatCantHappen() throws Exception {
        mvc.perform(asCustomer(post("/api/v1/bookings")).header("Idempotency-Key", UUID.randomUUID()).content("""
                        {"showId": %d, "seatIds": []}
                        """.formatted(showId)))
                .andExpect(status().isBadRequest());

        List<Long> eleven = LongStream.rangeClosed(1, 11).boxed().toList();
        mvc.perform(asCustomer(post("/api/v1/bookings")).header("Idempotency-Key", UUID.randomUUID()).content("""
                        {"showId": %d, "seatIds": %s}
                        """.formatted(showId, eleven)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Pick between 1 and 10 seats"));

        long scheduledShow = fixtures.scheduledShow().id();
        mvc.perform(asCustomer(post("/api/v1/bookings")).header("Idempotency-Key", UUID.randomUUID()).content("""
                        {"showId": %d, "seatIds": [1]}
                        """.formatted(scheduledShow)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SHOW_NOT_BOOKABLE"));
    }

    private ResultActions hold(UUID customer, String... labels) throws Exception {
        List<Long> ids = Arrays.stream(labels).map(seats::get).toList();
        return mvc.perform(asCustomer(post("/api/v1/bookings"), customer)
                .header("Idempotency-Key", UUID.randomUUID())
                .content("""
                        {"showId": %d, "seatIds": %s}
                        """.formatted(showId, ids)));
    }
}
