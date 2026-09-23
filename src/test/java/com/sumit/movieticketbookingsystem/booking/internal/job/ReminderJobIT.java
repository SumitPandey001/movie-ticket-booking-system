package com.sumit.movieticketbookingsystem.booking.internal.job;

import com.jayway.jsonpath.JsonPath;
import com.sumit.movieticketbookingsystem.Eventually;
import com.sumit.movieticketbookingsystem.MutableClock;
import com.sumit.movieticketbookingsystem.MutableClockConfiguration;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reminders go out 2 hours ahead. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class ReminderJobIT {

    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ReminderJob job;

    @Autowired
    private TransactionTemplate tx;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void onlyConfirmedBookingsForShowsStartingSoonAreRemindedAndOnlyOnce() throws Exception {
        BookingFixtures fixtures = new BookingFixtures(mvc, jdbc);
        LocalDate today = LocalDate.now(ZoneOffset.ofHoursMinutes(5, 30));
        BookableShow soon = fixtures.openShowOn(today.plusDays(2));
        BookableShow later = fixtures.openShowOn(today.plusDays(5));
        UUID asha = UUID.randomUUID();
        UUID reminded = confirmed(soon, asha, "A1", "A2");
        cancel(reminded, asha, "{\"seatIds\": [" + soon.seatIdsByLabel().get("A2") + "]}");
        UUID laterShow = confirmed(later, UUID.randomUUID(), "A1");
        UUID cancelled = confirmed(soon, UUID.randomUUID(), "A3");
        cancel(cancelled, ownerOf(cancelled), "{}");
        UUID held = hold(soon, UUID.randomUUID(), "A4");
        jdbc.sql("""
                        UPDATE app_user SET name = 'Asha', email = ?, phone = '+919800000002' WHERE id = ?""")
                .params("asha-" + asha.toString().substring(0, 8) + "@example.com", asha).update();

        clock.advance(Duration.between(clock.instant(), showStart(reminded).minus(Duration.ofHours(1))));
        job.runOnce();
        job.runOnce();                                                              // the next round: nothing new

        assertThat(reminderSentAt(reminded)).isNotNull();
        for (UUID notDue : List.of(laterShow, cancelled, held)) {
            assertThat(reminderSentAt(notDue)).isNull();
        }
        Eventually.until("the reminder email and SMS", WAIT, () -> jdbc.sql("""
                        SELECT count(*) FROM notification_log
                        WHERE booking_id = ? AND type = 'SHOW_REMINDER' AND status = 'SENT'""")
                .param(reminded).query(Long.class).single() == 2);
        assertThat(jdbc.sql("""
                        SELECT serialized_event FROM event_publication
                        WHERE event_type LIKE '%ReminderDue' AND serialized_event LIKE ?""")
                .param("%" + reminded + "%").query(String.class).list())
                .singleElement().asString().contains("\"seatLabels\":[\"A1\"]");           // A2 was cancelled
    }

    @Test
    void aBookingCancelledAfterBeingPickedIsNotReminded() throws Exception {
        BookableShow show = new BookingFixtures(mvc, jdbc).openShow();
        UUID customer = UUID.randomUUID();
        UUID bookingId = confirmed(show, customer, "B1");
        clock.advance(Duration.between(clock.instant(), showStart(bookingId).minus(Duration.ofMinutes(90))));
        assertThat(job.fetchBatch()).contains(bookingId);

        cancel(bookingId, customer, "{}");
        tx.executeWithoutResult(status -> job.process(bookingId));

        assertThat(reminderSentAt(bookingId)).isNull();
    }

    private UUID hold(BookableShow show, UUID customer, String... labels) throws Exception {
        String response = mvc.perform(asCustomer(post("/api/v1/bookings"), customer)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("{\"showId\": %d, \"seatIds\": %s}".formatted(show.id(), show.seats(labels))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.bookingId"));
    }

    private UUID confirmed(BookableShow show, UUID customer, String... labels) throws Exception {
        UUID bookingId = hold(show, customer, labels);
        mvc.perform(asCustomer(post("/api/v1/bookings/{id}/payments", bookingId), customer)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("""
                                {"details": {"type": "UPI", "vpa": "asha@okbank"}, "simulate": "SUCCESS"}"""))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
        return bookingId;
    }

    private void cancel(UUID bookingId, UUID customer, String body) throws Exception {
        mvc.perform(asCustomer(post("/api/v1/bookings/{id}/cancellations", bookingId), customer)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content(body))
                .andExpect(status().isOk());
    }

    private UUID ownerOf(UUID bookingId) {
        return jdbc.sql("SELECT user_id FROM booking WHERE id = ?").param(bookingId).query(UUID.class).single();
    }

    private Instant showStart(UUID bookingId) {
        return jdbc.sql("SELECT show_start_time FROM booking WHERE id = ?")
                .param(bookingId).query(Instant.class).single();
    }

    private Instant reminderSentAt(UUID bookingId) {
        return jdbc.sql("SELECT reminder_sent_at FROM booking WHERE id = ?")
                .param(bookingId).query(Instant.class).optional().orElse(null);
    }
}
