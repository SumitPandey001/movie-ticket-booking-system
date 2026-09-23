package com.sumit.movieticketbookingsystem.booking.internal.job;

import com.jayway.jsonpath.JsonPath;
import com.sumit.movieticketbookingsystem.MutableClock;
import com.sumit.movieticketbookingsystem.MutableClockConfiguration;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Holds last 8 minutes; a started payment keeps its seats 5 minutes, plus a 2-minute grace for the sweeper. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class HoldExpiryJobIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private HoldExpiryJob job;

    @Autowired
    private TransactionTemplate tx;

    private BookableShow show;

    @BeforeEach
    void setUp() throws Exception {
        show = new BookingFixtures(mvc, jdbc).openShow();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void aLapsedHoldGivesItsSeatsAndCouponBack() throws Exception {
        String code = new CouponFixtures(mvc).coupon("\"discountType\": \"FLAT\", \"discountValue\": 1000");
        UUID lapsed = hold(code, "A1");
        UUID fresh = hold(null, "A2");
        clock.advance(Duration.ofMinutes(8));
        jdbc.sql("UPDATE booking SET hold_expires_at = hold_expires_at + interval '1 minute' WHERE id = ?")
                .param(fresh).update();                                             // still has a minute

        job.runOnce();

        assertThat(statusOf(lapsed)).isEqualTo("EXPIRED");
        assertThat(seatStatus("A1")).isEqualTo("AVAILABLE");
        assertThat(jdbc.sql("SELECT status FROM coupon_redemption WHERE booking_id = ?")
                .param(lapsed).query(String.class).single()).isEqualTo("RELEASED");
        assertThat(statusOf(fresh)).isEqualTo("HELD");
        assertThat(seatStatus("A2")).isEqualTo("HELD");
    }

    @Test
    void aPendingPaymentGetsItsGraceBeforeItsSeatsGo() throws Exception {
        UUID pending = hold(null, "A3");
        jdbc.sql("UPDATE booking SET status = 'PAYMENT_PENDING' WHERE id = ?").param(pending).update();

        clock.advance(Duration.ofMinutes(9));                                       // past the hold, inside grace
        job.runOnce();
        assertThat(statusOf(pending)).isEqualTo("PAYMENT_PENDING");

        clock.advance(Duration.ofMinutes(1));                                       // 8 + 2 minutes
        job.runOnce();
        assertThat(statusOf(pending)).isEqualTo("EXPIRED");
        assertThat(seatStatus("A3")).isEqualTo("AVAILABLE");
    }

    @Test
    void aBookingThatMovedOnAfterBeingPickedIsLeftAlone() throws Exception {
        UUID bookingId = hold(null, "A4");
        clock.advance(Duration.ofMinutes(9));
        assertThat(job.fetchBatch()).contains(bookingId);

        // meanwhile the customer started paying, which keeps the seats for the payment window
        jdbc.sql("""
                        UPDATE booking SET status = 'PAYMENT_PENDING', hold_expires_at = ?, version = version + 1
                        WHERE id = ?""")
                .params(clock.instant().plus(Duration.ofMinutes(5)).atOffset(ZoneOffset.UTC), bookingId).update();
        tx.executeWithoutResult(status -> job.process(bookingId));               // with the id it picked earlier

        assertThat(statusOf(bookingId)).isEqualTo("PAYMENT_PENDING");
        assertThat(seatStatus("A4")).isEqualTo("HELD");
    }

    private UUID hold(String coupon, String label) throws Exception {
        String response = mvc.perform(asCustomer(post("/api/v1/bookings"))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("{\"showId\": %d, \"seatIds\": %s, \"couponCode\": %s}".formatted(show.id(),
                                show.seats(label), coupon == null ? "null" : "\"" + coupon + "\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.bookingId"));
    }

    private String statusOf(UUID bookingId) {
        return jdbc.sql("SELECT status FROM booking WHERE id = ?").param(bookingId).query(String.class).single();
    }

    private String seatStatus(String label) {
        return jdbc.sql("SELECT status FROM show_seat WHERE show_id = ? AND layout_seat_id = ?")
                .params(show.id(), show.seatIdsByLabel().get(label)).query(String.class).single();
    }
}
