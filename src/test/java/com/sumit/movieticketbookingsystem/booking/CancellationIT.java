package com.sumit.movieticketbookingsystem.booking;

import com.jayway.jsonpath.JsonPath;
import com.sumit.movieticketbookingsystem.MutableClock;
import com.sumit.movieticketbookingsystem.MutableClockConfiguration;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import com.sumit.movieticketbookingsystem.pricing.CouponFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Customers cancelling under the seeded Standard policy (24h+ 100%, 4h+ 50%, less 0%; fees kept) unless a test
 * says otherwise. Row A is REGULAR at ₹200: ₹259.60 a seat with fee and GST.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class CancellationIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MutableClock clock;

    private BookableShow show;
    private UUID customer;

    @BeforeEach
    void setUp() throws Exception {
        show = new BookingFixtures(mvc, jdbc).openShow();
        customer = UUID.randomUUID();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void cancellingSeatByStepRefundsWhatThePreviewSaid() throws Exception {
        String code = new CouponFixtures(mvc).coupon("\"discountType\": \"FLAT\", \"discountValue\": 3000");
        String bookingId = confirmedBooking(code, "A1", "A2", "A3");
        long a1 = show.seatIdsByLabel().get("A1");

        String preview = mvc.perform(asCustomer(get("/api/v1/bookings/{id}/refund-quote", bookingId), customer)
                        .param("seatIds", String.valueOf(a1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats").value(contains("A1")))
                .andExpect(jsonPath("$.refundPercent").value(100))
                .andReturn().getResponse().getContentAsString();
        long previewRefund = JsonPath.<Number>read(preview, "$.refundPaise").longValue();
        assertThat(seatStatus("A1")).isEqualTo("BOOKED");                        // a preview changes nothing

        UUID key = UUID.randomUUID();
        String first = cancel(bookingId, "{\"seatIds\": [" + a1 + "]}", key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundPaise").value(previewRefund))
                .andExpect(jsonPath("$.refundId").isNotEmpty())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.booking.seats[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.booking.seats[1].status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(cancel(bookingId, "{\"seatIds\": [" + a1 + "]}", key)             // a retry, not a second cancel
                .andReturn().getResponse().getContentAsString()).isEqualTo(first);
        assertThat(seatStatus("A1")).isEqualTo("AVAILABLE");
        assertThat(couponStatus(bookingId)).isEqualTo("CONSUMED");               // two seats still use it

        cancel(bookingId, "{}", UUID.randomUUID())
                .andExpect(jsonPath("$.booking.status").value("CANCELLED"))
                .andExpect(jsonPath("$.booking.seats[2].status").value("CANCELLED"));
        assertThat(couponStatus(bookingId)).isEqualTo("RELEASED");
        assertThat(jdbc.sql("SELECT count(*) FROM cancellation WHERE booking_id = ?")
                .param(UUID.fromString(bookingId)).query(Long.class).single()).isEqualTo(2L);

        long refunded = jdbc.sql("SELECT sum(amount_paise) FROM refund WHERE booking_id = ?")
                .param(UUID.fromString(bookingId)).query(Long.class).single();
        long paid = jdbc.sql("SELECT amount_paise FROM payment WHERE booking_id = ? AND status = 'SUCCESS'")
                .param(UUID.fromString(bookingId)).query(Long.class).single();
        assertThat(refunded).isEqualTo(paid - 3 * 2360);                          // everything but the fees

        cancel(bookingId, "{}", UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void theRefundShrinksAsTheShowGetsCloserAndThenCancellingCloses() throws Exception {
        String bookingId = confirmedBooking(null, "A4");
        Instant start = Instant.parse(JsonPath.read(booking(bookingId), "$.showStartTime"));

        clock.advance(Duration.between(clock.instant(), start.minus(Duration.ofHours(5))));
        mvc.perform(asCustomer(get("/api/v1/bookings/{id}/refund-quote", bookingId), customer))
                .andExpect(jsonPath("$.refundPercent").value(50))
                .andExpect(jsonPath("$.refundPaise").value(11800))                  // half of the ₹236 ticket
                .andExpect(jsonPath("$.retainedPaise").value(11800 + 2360));

        clock.advance(Duration.ofHours(4).plusMinutes(31));                         // 29 minutes to go
        mvc.perform(asCustomer(get("/api/v1/bookings/{id}/refund-quote", bookingId), customer))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CANCELLATION_CLOSED"));
        cancel(bookingId, "{}", UUID.randomUUID())
                .andExpect(jsonPath("$.code").value("CANCELLATION_CLOSED"));
    }

    @Test
    void aNonRefundableTicketCanStillBeGivenUp() throws Exception {
        long policy = jdbc.sql("""
                        INSERT INTO refund_policy (name, type) VALUES (?, 'NON_REFUNDABLE') RETURNING id""")
                .param("NoRefund-" + UUID.randomUUID()).query(Long.class).single();
        jdbc.sql("UPDATE show SET refund_policy_id = ? WHERE id = ?").params(policy, show.id()).update();
        String bookingId = confirmedBooking(null, "B2");

        cancel(bookingId, "{}", UUID.randomUUID())
                .andExpect(jsonPath("$.refundPercent").value(0))
                .andExpect(jsonPath("$.refundPaise").value(0))
                .andExpect(jsonPath("$.refundId").doesNotExist())
                .andExpect(jsonPath("$.booking.status").value("CANCELLED"));
        assertThat(seatStatus("B2")).isEqualTo("AVAILABLE");
        assertThat(jdbc.sql("SELECT count(*) FROM refund WHERE booking_id = ?")
                .param(UUID.fromString(bookingId)).query(Long.class).single()).isZero();
    }

    @Test
    void theDatabaseRefusesToRefundMoreThanWasPaid() throws Exception {
        String bookingId = confirmedBooking(null, "B3");

        assertThatThrownBy(() -> jdbc.sql("""
                        UPDATE payment SET refunded_paise = amount_paise + 1 WHERE booking_id = ?""")
                .param(UUID.fromString(bookingId)).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("payment_refund_within_amount");
    }

    @Test
    void twoCancelsAtOnceLeaveOneWinnerAndOneClearNo() throws Exception {
        String bookingId = confirmedBooking(null, "A5");

        List<Integer> statuses;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = pool.invokeAll(List.<Callable<Integer>>of(
                    () -> cancel(bookingId, "{}", UUID.randomUUID()).andReturn().getResponse().getStatus(),
                    () -> cancel(bookingId, "{}", UUID.randomUUID()).andReturn().getResponse().getStatus()));
            statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.sql("SELECT count(*) FROM refund WHERE booking_id = ?")
                .param(UUID.fromString(bookingId)).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void someoneElsesBookingCantBeCancelled() throws Exception {
        String bookingId = confirmedBooking(null, "B4");

        mvc.perform(asCustomer(post("/api/v1/bookings/{id}/cancellations", bookingId))
                        .header("Idempotency-Key", UUID.randomUUID()).content("{}"))
                .andExpect(status().isNotFound());
    }

    private String confirmedBooking(String couponCode, String... labels) throws Exception {
        String hold = mvc.perform(asCustomer(post("/api/v1/bookings"), customer)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("""
                                {"showId": %d, "seatIds": %s, "couponCode": %s}
                                """.formatted(show.id(), show.seats(labels),
                                couponCode == null ? "null" : "\"" + couponCode + "\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String bookingId = JsonPath.read(hold, "$.bookingId");
        mvc.perform(asCustomer(post("/api/v1/bookings/{id}/payments", bookingId), customer)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("""
                                {"details": {"type": "UPI", "vpa": "asha@okbank"}, "simulate": "SUCCESS"}"""))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
        return bookingId;
    }

    private ResultActions cancel(String bookingId, String body, UUID key) throws Exception {
        return mvc.perform(asCustomer(post("/api/v1/bookings/{id}/cancellations", bookingId), customer)
                .header("Idempotency-Key", key)
                .content(body));
    }

    private String booking(String bookingId) throws Exception {
        return mvc.perform(asCustomer(get("/api/v1/bookings/{id}", bookingId), customer))
                .andReturn().getResponse().getContentAsString();
    }

    private String seatStatus(String label) {
        return jdbc.sql("SELECT status FROM show_seat WHERE show_id = ? AND layout_seat_id = ?")
                .params(show.id(), show.seatIdsByLabel().get(label)).query(String.class).single();
    }

    private String couponStatus(String bookingId) {
        return jdbc.sql("SELECT status FROM coupon_redemption WHERE booking_id = ?")
                .param(UUID.fromString(bookingId)).query(String.class).single();
    }
}
