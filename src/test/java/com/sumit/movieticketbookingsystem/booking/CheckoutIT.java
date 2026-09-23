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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class CheckoutIT {

    private static final String UPI = """
            {"type": "UPI", "vpa": "asha@okbank"}""";

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
    void successfulPaymentConfirmsTheBooking() throws Exception {
        String code = new CouponFixtures(mvc).coupon("\"discountType\": \"FLAT\", \"discountValue\": 1000");
        String bookingId = hold(code, "A1", "A2");

        pay(bookingId, UPI, "SUCCESS", UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));

        mvc.perform(asCustomer(get("/api/v1/shows/{id}/seats", show.id())))
                .andExpect(jsonPath("$.seats[?(@.label == 'A1')].status").value(contains("BOOKED")))
                .andExpect(jsonPath("$.seats[?(@.label == 'A2')].status").value(contains("BOOKED")));
        assertThat(jdbc.sql("SELECT status FROM coupon_redemption WHERE booking_id = ?")
                .param(UUID.fromString(bookingId)).query(String.class).single()).isEqualTo("CONSUMED");
        assertThat(jdbc.sql("SELECT masked_details FROM payment WHERE booking_id = ? AND status = 'SUCCESS'")
                .param(UUID.fromString(bookingId)).query(String.class).single()).isEqualTo("asha@okbank");
    }

    @Test
    void declinedPaymentGivesTheSeatsAndCouponBack() throws Exception {
        String code = new CouponFixtures(mvc).coupon("\"discountType\": \"FLAT\", \"discountValue\": 1000");
        String bookingId = hold(code, "B1");

        pay(bookingId, UPI, "FAILURE", UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Declined by issuer (simulated)"))
                .andExpect(jsonPath("$.booking.status").value("FAILED"));

        assertThat(seatStatus("B1")).isEqualTo("AVAILABLE");
        assertThat(jdbc.sql("SELECT used_count FROM coupon WHERE code = ?").param(code).query(Integer.class).single())
                .isZero();
        hold(code, "B1");                                         // the customer can simply try again
    }

    @Test
    void invalidPaymentDetailsLeaveTheHoldAsItWas() throws Exception {
        String bookingId = hold(null, "A3");

        pay(bookingId, """
                {"type": "CARD", "number": "4242 4242 4242 4241", "expiryMonth": 12, "expiryYear": 2030,
                 "cvv": "123", "holderName": "Asha"}""", "SUCCESS", UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The card number isn't valid"));
        mvc.perform(asCustomer(get("/api/v1/bookings/{id}", bookingId), customer))
                .andExpect(jsonPath("$.status").value("HELD"));

        pay(bookingId, """
                {"type": "CARD", "number": "4242 4242 4242 4242", "expiryMonth": 12, "expiryYear": 2030,
                 "cvv": "123", "holderName": "Asha"}""", "SUCCESS", UUID.randomUUID())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
    }

    @Test
    void startingPaymentLateStillLeavesTimeToFinish() throws Exception {
        String bookingId = hold(null, "A4");
        clock.advance(Duration.ofMinutes(6));                     // 2 of the 8 minutes left

        String response = pay(bookingId, UPI, "SUCCESS", UUID.randomUUID())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                .andReturn().getResponse().getContentAsString();

        Instant heldUntil = Instant.parse(JsonPath.read(response, "$.booking.holdExpiresAt"));
        assertThat(heldUntil).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));
    }

    @Test
    void anExpiredHoldCantBePaidFor() throws Exception {
        String bookingId = hold(null, "A5");
        clock.advance(Duration.ofMinutes(9));

        pay(bookingId, UPI, "SUCCESS", UUID.randomUUID())
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
    }

    @Test
    void aRetryReplaysTheAnswerAndAConfirmedBookingIsntPaidAgain() throws Exception {
        String bookingId = hold(null, "B2");
        UUID key = UUID.randomUUID();
        String first = pay(bookingId, UPI, "SUCCESS", key).andReturn().getResponse().getContentAsString();

        String retry = pay(bookingId, UPI, "SUCCESS", key).andReturn().getResponse().getContentAsString();
        assertThat(retry).isEqualTo(first);
        assertThat(jdbc.sql("SELECT count(*) FROM payment WHERE booking_id = ?")
                .param(UUID.fromString(bookingId)).query(Long.class).single()).isEqualTo(1L);

        pay(bookingId, UPI, "SUCCESS", UUID.randomUUID())         // a new attempt, not a retry
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    private String hold(String couponCode, String... labels) throws Exception {
        String response = mvc.perform(asCustomer(post("/api/v1/bookings"), customer)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("""
                                {"showId": %d, "seatIds": %s, "couponCode": %s}
                                """.formatted(show.id(), show.seats(labels).stream().sorted().toList(),
                                couponCode == null ? "null" : "\"" + couponCode + "\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.bookingId");
    }

    private ResultActions pay(String bookingId, String details, String simulate, UUID key) throws Exception {
        return mvc.perform(asCustomer(post("/api/v1/bookings/{id}/payments", bookingId), customer)
                .header("Idempotency-Key", key)
                .content("""
                        {"details": %s, "simulate": "%s"}
                        """.formatted(details, simulate)));
    }

    private String seatStatus(String label) {
        return jdbc.sql("SELECT status FROM show_seat WHERE show_id = ? AND layout_seat_id = ?")
                .params(show.id(), show.seatIdsByLabel().get(label)).query(String.class).single();
    }
}
