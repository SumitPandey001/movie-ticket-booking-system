package com.sumit.movieticketbookingsystem.booking;

import com.jayway.jsonpath.JsonPath;
import com.sumit.movieticketbookingsystem.Eventually;
import com.sumit.movieticketbookingsystem.MutableClock;
import com.sumit.movieticketbookingsystem.MutableClockConfiguration;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import com.sumit.movieticketbookingsystem.booking.internal.service.CheckoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DELAYED simulated payments answer "pending" and succeed two seconds later, through the outbox.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@TestPropertySource(properties = "booking.payment.simulated-delay=PT2S")
class DelayedAndLatePaymentIT {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private CheckoutService checkout;

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
    void delayedPaymentConfirmsTheBookingWhenItComesThrough() throws Exception {
        String bookingId = hold(customer, "A1");

        payDelayed(bookingId, UUID.randomUUID())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.booking.status").value("PAYMENT_PENDING"));

        Eventually.until("the booking to be confirmed", WAIT, () -> "CONFIRMED".equals(bookingStatus(bookingId)));
        assertThat(seatStatus("A1")).isEqualTo("BOOKED");
        Eventually.until("the PaymentSucceeded publication to complete", WAIT, () -> jdbc.sql("""
                        SELECT count(*) FROM event_publication
                        WHERE event_type LIKE '%PaymentSucceeded' AND completion_date IS NOT NULL
                          AND serialized_event LIKE ?
                        """).param("%" + bookingId + "%").query(Long.class).single() == 1);

        checkout.completePayment(UUID.fromString(bookingId), true);            // the same event delivered again
        assertThat(bookingStatus(bookingId)).isEqualTo("CONFIRMED");
        assertThat(refundCount(bookingId)).isZero();
    }

    @Test
    void paymentThatLandsAfterTheSeatsWereTakenIsRefundedInFull() throws Exception {
        String bookingId = hold(customer, "B1");
        payDelayed(bookingId, UUID.randomUUID()).andExpect(status().isAccepted());

        clock.advance(Duration.ofMinutes(9));                                  // past the hold and payment window
        hold(UUID.randomUUID(), "B1");                                         // someone else takes the seat

        Eventually.until("the late payment to be refunded", WAIT,
                () -> "COMPLETED".equals(refund(bookingId).get("status")));
        Map<String, Object> refund = refund(bookingId);
        long paid = jdbc.sql("SELECT amount_paise FROM payment WHERE booking_id = ?")
                .param(UUID.fromString(bookingId)).query(Long.class).single();
        assertThat(refund).containsEntry("reason", "LATE_PAYMENT").containsEntry("amount_paise", paid);
        assertThat(bookingStatus(bookingId)).isEqualTo("EXPIRED");
        assertThat(seatStatus("B1")).isEqualTo("HELD");                        // still the other customer's

        checkout.completePayment(UUID.fromString(bookingId), true);            // redelivered: no second refund
        assertThat(refundCount(bookingId)).isEqualTo(1);
    }

    @Test
    void retryingAPendingPaymentReplaysThe202() throws Exception {
        String bookingId = hold(customer, "A2");
        UUID key = UUID.randomUUID();
        String first = payDelayed(bookingId, key).andReturn().getResponse().getContentAsString();

        String retry = payDelayed(bookingId, key)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(retry).isEqualTo(first);
        Eventually.until("the booking to be confirmed", WAIT, () -> "CONFIRMED".equals(bookingStatus(bookingId)));
    }

    private String hold(UUID who, String label) throws Exception {
        String response = mvc.perform(asCustomer(post("/api/v1/bookings"), who)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("""
                                {"showId": %d, "seatIds": %s}
                                """.formatted(show.id(), show.seats(label))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.bookingId");
    }

    private ResultActions payDelayed(String bookingId, UUID key) throws Exception {
        return mvc.perform(asCustomer(post("/api/v1/bookings/{id}/payments", bookingId), customer)
                .header("Idempotency-Key", key)
                .content("""
                        {"details": {"type": "UPI", "vpa": "asha@okbank"}, "simulate": "DELAYED"}
                        """));
    }

    private String bookingStatus(String bookingId) {
        return jdbc.sql("SELECT status FROM booking WHERE id = ?").param(UUID.fromString(bookingId))
                .query(String.class).single();
    }

    private String seatStatus(String label) {
        return jdbc.sql("SELECT status FROM show_seat WHERE show_id = ? AND layout_seat_id = ?")
                .params(show.id(), show.seatIdsByLabel().get(label)).query(String.class).single();
    }

    private Map<String, Object> refund(String bookingId) {
        return jdbc.sql("SELECT status, reason, amount_paise FROM refund WHERE booking_id = ?")
                .param(UUID.fromString(bookingId)).query().listOfRows().stream().findFirst().orElse(Map.of());
    }

    private long refundCount(String bookingId) {
        return jdbc.sql("SELECT count(*) FROM refund WHERE booking_id = ?").param(UUID.fromString(bookingId))
                .query(Long.class).single();
    }
}
