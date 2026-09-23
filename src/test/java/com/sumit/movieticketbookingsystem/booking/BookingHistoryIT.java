package com.sumit.movieticketbookingsystem.booking;

import com.jayway.jsonpath.JsonPath;
import com.sumit.movieticketbookingsystem.Eventually;
import com.sumit.movieticketbookingsystem.MutableClock;
import com.sumit.movieticketbookingsystem.MutableClockConfiguration;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import org.junit.jupiter.api.AfterEach;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class BookingHistoryIT {

    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MutableClock clock;

    private BookingFixtures fixtures;
    private UUID customer;

    @BeforeEach
    void setUp() {
        fixtures = new BookingFixtures(mvc, jdbc);
        customer = UUID.randomUUID();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void historyIsSplitIntoUpcomingPastAndCancelled() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.ofHoursMinutes(5, 30));
        BookableShow soon = fixtures.openShowOn(today.plusDays(2));
        BookableShow later = fixtures.openShowOn(today.plusDays(5));
        String laterBooking = confirmed(later, "A1");
        String soonBooking = confirmed(soon, "A1", "A2");
        cancel(soonBooking, "{\"seatIds\": [" + soon.seatIdsByLabel().get("A2") + "]}");
        String cancelled = confirmed(soon, "A3");
        cancel(cancelled, "{}");
        hold(later, "A2");                                                          // a hold isn't history

        history("UPCOMING", 0, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].bookingId").value(contains(soonBooking, laterBooking)))
                .andExpect(jsonPath("$.items[0].seats").value(contains("A1")))
                .andExpect(jsonPath("$.items[0].cancelledSeats").value(contains("A2")))
                .andExpect(jsonPath("$.items[0].movieTitle").isNotEmpty())
                .andExpect(jsonPath("$.items[0].theaterName").isNotEmpty());
        history("UPCOMING", 1, 1)
                .andExpect(jsonPath("$.items[*].bookingId").value(contains(laterBooking)))
                .andExpect(jsonPath("$.totalPages").value(2));
        history("CANCELLED", 0, 10)
                .andExpect(jsonPath("$.items[*].bookingId").value(contains(cancelled)))
                .andExpect(jsonPath("$.items[0].seats").value(hasSize(0)));
        history("PAST", 0, 10).andExpect(jsonPath("$.items").value(hasSize(0)));

        Instant soonStart = Instant.parse(JsonPath.read(details(soonBooking), "$.showStartTime"));
        clock.advance(Duration.between(clock.instant(), soonStart.plusSeconds(60)));
        history("PAST", 0, 10).andExpect(jsonPath("$.items[*].bookingId").value(contains(soonBooking)));
        history("UPCOMING", 0, 10).andExpect(jsonPath("$.items[*].bookingId").value(contains(laterBooking)));

        mvc.perform(asCustomer(get("/api/v1/bookings").param("view", "UPCOMING")))   // another customer
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void detailsShowCancellationsAndWhereTheRefundStands() throws Exception {
        BookableShow show = fixtures.openShow();
        String bookingId = confirmed(show, "A1", "A2");
        String cancellationId = JsonPath.read(
                cancel(bookingId, "{\"seatIds\": [" + show.seatIdsByLabel().get("A1") + "]}"), "$.cancellationId");

        Eventually.until("the refund to complete", WAIT,
                () -> details(bookingId).contains("\"status\":\"COMPLETED\""));
        mvc.perform(asCustomer(get("/api/v1/bookings/{id}", bookingId), customer))
                .andExpect(jsonPath("$.bookingId").value(bookingId))                  // same fields as a hold
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.movieTitle").isNotEmpty())
                .andExpect(jsonPath("$.cancellations[0].cancellationId").value(cancellationId))
                .andExpect(jsonPath("$.cancellations[0].seats").value(contains("A1")))
                .andExpect(jsonPath("$.cancellations[0].refundPercent").value(100))
                .andExpect(jsonPath("$.payment.method").value("UPI"))
                .andExpect(jsonPath("$.payment.paidWith").value("asha@okbank"))
                .andExpect(jsonPath("$.payment.refunds[0].cancellationId").value(cancellationId))
                .andExpect(jsonPath("$.payment.refunds[0].reason").value("CUSTOMER"))
                .andExpect(jsonPath("$.payment.refunds[0].status").value("COMPLETED"));
    }

    @Test
    void anUnpaidBookingHasNoPaymentYet() throws Exception {
        String bookingId = hold(fixtures.openShow(), "A1");

        mvc.perform(asCustomer(get("/api/v1/bookings/{id}", bookingId), customer))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.cancellations").value(hasSize(0)))
                .andExpect(jsonPath("$.payment").doesNotExist());
    }

    @Test
    void historyNeedsAViewAndASensiblePageSize() throws Exception {
        mvc.perform(asCustomer(get("/api/v1/bookings"), customer)).andExpect(status().isBadRequest());
        history("UPCOMING", 0, 500).andExpect(status().isBadRequest());
        history("SOMEDAY", 0, 10).andExpect(status().isBadRequest());
    }

    private ResultActions history(String view, int page, int size) throws Exception {
        return mvc.perform(asCustomer(get("/api/v1/bookings"), customer)
                .param("view", view).param("page", String.valueOf(page)).param("size", String.valueOf(size)));
    }

    private String hold(BookableShow show, String... labels) throws Exception {
        String response = mvc.perform(asCustomer(post("/api/v1/bookings"), customer)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("{\"showId\": %d, \"seatIds\": %s}".formatted(show.id(), show.seats(labels))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.bookingId");
    }

    private String confirmed(BookableShow show, String... labels) throws Exception {
        String bookingId = hold(show, labels);
        mvc.perform(asCustomer(post("/api/v1/bookings/{id}/payments", bookingId), customer)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("""
                                {"details": {"type": "UPI", "vpa": "asha@okbank"}, "simulate": "SUCCESS"}"""))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
        return bookingId;
    }

    private String cancel(String bookingId, String body) throws Exception {
        return mvc.perform(asCustomer(post("/api/v1/bookings/{id}/cancellations", bookingId), customer)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String details(String bookingId) {
        try {
            return mvc.perform(asCustomer(get("/api/v1/bookings/{id}", bookingId), customer))
                    .andReturn().getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
