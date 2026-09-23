package com.sumit.movieticketbookingsystem.booking;

import com.jayway.jsonpath.JsonPath;
import com.sumit.movieticketbookingsystem.Eventually;
import com.sumit.movieticketbookingsystem.MutableClockConfiguration;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import com.sumit.movieticketbookingsystem.pricing.CouponFixtures;
import com.sumit.movieticketbookingsystem.show.ShowCancelled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An admin cancels a show with every kind of booking on it. Same setup as DelayedAndLatePaymentIT, so the two
 * share a Spring context: a DELAYED payment succeeds two seconds later.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@TestPropertySource(properties = "booking.payment.simulated-delay=PT2S")
class ShowCancellationIT {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void everyBookingIsReleasedOrRefundedInFull() throws Exception {
        BookableShow show = new BookingFixtures(mvc, jdbc).openShow();
        String code = new CouponFixtures(mvc).coupon("\"discountType\": \"FLAT\", \"discountValue\": 2000");
        String paid1 = confirmed(show, code, "A1");
        String paid2 = confirmed(show, null, "A2", "A3");
        String partlyCancelled = confirmed(show, null, "B1", "B2");
        mvc.perform(asCustomer(post("/api/v1/bookings/{id}/cancellations", partlyCancelled), ownerOf(partlyCancelled))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("{\"seatIds\": [" + show.seatIdsByLabel().get("B1") + "]}"))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
        String held = hold(show, UUID.randomUUID(), null, "A4");
        String pending = hold(show, UUID.randomUUID(), null, "A5");
        pay(pending, "DELAYED").andExpect(status().isAccepted());

        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/cancel", show.id()))).andExpect(status().isOk());

        Eventually.until("the confirmed bookings to be cancelled", WAIT,
                () -> Stream.of(paid1, paid2, partlyCancelled).allMatch(id -> "CANCELLED".equals(statusOf(id))));
        assertThat(statusOf(held)).isEqualTo("RELEASED");
        for (String bookingId : List.of(paid1, paid2)) {
            assertThat(refunded(bookingId, "SHOW_CANCELLED")).isEqualTo(paidFor(bookingId));   // fees included
        }
        long firstRefund = refunded(partlyCancelled, "CUSTOMER");                   // B1, under the policy
        assertThat(refunded(partlyCancelled, "SHOW_CANCELLED")).isEqualTo(paidFor(partlyCancelled) / 2);   // B2
        assertThat(firstRefund).isLessThan(paidFor(partlyCancelled) / 2);
        assertThat(jdbc.sql("SELECT status FROM coupon_redemption WHERE booking_id = ?")
                .param(UUID.fromString(paid1)).query(String.class).single()).isEqualTo("RELEASED");

        // the delayed payment lands on a cancelled show and goes straight back
        Eventually.until("the pending payment to be refunded", WAIT, () -> refunded(pending, "LATE_PAYMENT") > 0);
        assertThat(statusOf(pending)).isEqualTo("EXPIRED");
        assertThat(refunded(pending, "LATE_PAYMENT")).isEqualTo(paidFor(pending));
        assertThat(jdbc.sql("SELECT count(*) FROM show_seat WHERE show_id = ? AND status <> 'AVAILABLE'")
                .param(show.id()).query(Long.class).single()).isZero();

        // delivered again: nothing left to do, nothing refunded twice
        long refundsBefore = refundCount(show);
        tx.executeWithoutResult(status -> events.publishEvent(new ShowCancelled(show.id(), Instant.now())));
        Eventually.until("the redelivery to complete", WAIT, () -> completedDeliveries(show.id()) == 2);
        assertThat(refundCount(show)).isEqualTo(refundsBefore);
    }

    @Test
    void aHoldOnACancelledShowCantBePaidFor() throws Exception {
        BookableShow show = new BookingFixtures(mvc, jdbc).openShow();
        String bookingId = hold(show, UUID.randomUUID(), null, "A1");
        jdbc.sql("UPDATE show SET status = 'CANCELLED' WHERE id = ?").param(show.id()).update();  // no listener run

        pay(bookingId, "SUCCESS")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SHOW_NOT_BOOKABLE"));
        assertThat(statusOf(bookingId)).isEqualTo("HELD");
    }

    private String confirmed(BookableShow show, String coupon, String... labels) throws Exception {
        String bookingId = hold(show, UUID.randomUUID(), coupon, labels);
        pay(bookingId, "SUCCESS").andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
        return bookingId;
    }

    private String hold(BookableShow show, UUID customer, String coupon, String... labels) throws Exception {
        String response = mvc.perform(asCustomer(post("/api/v1/bookings"), customer)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("{\"showId\": %d, \"seatIds\": %s, \"couponCode\": %s}".formatted(show.id(),
                                show.seats(labels), coupon == null ? "null" : "\"" + coupon + "\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.bookingId");
    }

    private ResultActions pay(String bookingId, String simulate) throws Exception {
        return mvc.perform(asCustomer(post("/api/v1/bookings/{id}/payments", bookingId), ownerOf(bookingId))
                .header("Idempotency-Key", UUID.randomUUID())
                .content("""
                        {"details": {"type": "UPI", "vpa": "asha@okbank"}, "simulate": "%s"}""".formatted(simulate)));
    }

    private UUID ownerOf(String bookingId) {
        return jdbc.sql("SELECT user_id FROM booking WHERE id = ?")
                .param(UUID.fromString(bookingId)).query(UUID.class).single();
    }

    private String statusOf(String bookingId) {
        return jdbc.sql("SELECT status FROM booking WHERE id = ?")
                .param(UUID.fromString(bookingId)).query(String.class).single();
    }

    private long paidFor(String bookingId) {
        return jdbc.sql("SELECT amount_paise FROM payment WHERE booking_id = ? AND status = 'SUCCESS'")
                .param(UUID.fromString(bookingId)).query(Long.class).single();
    }

    private long refunded(String bookingId, String reason) {
        return jdbc.sql("SELECT coalesce(sum(amount_paise), 0) FROM refund WHERE booking_id = ? AND reason = ?")
                .params(UUID.fromString(bookingId), reason).query(Long.class).single();
    }

    private long refundCount(BookableShow show) {
        return jdbc.sql("SELECT count(*) FROM refund r JOIN booking b ON b.id = r.booking_id WHERE b.show_id = ?")
                .param(show.id()).query(Long.class).single();
    }

    private long completedDeliveries(long showId) {
        return jdbc.sql("""
                        SELECT count(*) FROM event_publication
                        WHERE listener_id LIKE '%ShowCancelledListener%' AND completion_date IS NOT NULL
                          AND serialized_event LIKE ?""")
                .param("%\"showId\":" + showId + ",%").query(Long.class).single();
    }
}
