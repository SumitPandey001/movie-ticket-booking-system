package com.sumit.movieticketbookingsystem.notification;

import com.jayway.jsonpath.JsonPath;
import com.sumit.movieticketbookingsystem.Eventually;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingCancelled;
import com.sumit.movieticketbookingsystem.booking.BookingConfirmed;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import com.sumit.movieticketbookingsystem.booking.CancellationReason;
import com.sumit.movieticketbookingsystem.payment.RefundCompleted;
import com.sumit.movieticketbookingsystem.payment.RefundReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Emails end up in a real Mailpit, which we read back through its API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationIT {

    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private GenericContainer<?> mailpitContainer;

    private RestClient mailpit;
    private UUID customer;
    private String email;

    @BeforeEach
    void setUp() {
        mailpit = RestClient.create(
                "http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(8025));
        customer = UUID.randomUUID();
        email = "asha-" + customer.toString().substring(0, 8) + "@example.com";
    }

    @Test
    void payingForAHoldEmailsTheTickets() throws Exception {
        BookableShow show = new BookingFixtures(mvc, jdbc).openShow();
        String hold = mvc.perform(asAsha(post("/api/v1/bookings"))
                        .content("""
                                {"showId": %d, "seatIds": %s}""".formatted(show.id(), show.seats("A1", "A2"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String bookingId = JsonPath.read(hold, "$.bookingId");
        String bookingRef = JsonPath.read(hold, "$.bookingRef");

        mvc.perform(asAsha(post("/api/v1/bookings/{id}/payments", bookingId))
                        .content("""
                                {"details": {"type": "UPI", "vpa": "asha@okbank"}, "simulate": "SUCCESS"}"""))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));

        Eventually.until("the confirmation email", WAIT, () -> emailsTo(email) == 1);
        String message = latestEmailTo(email);
        assertThat(JsonPath.<String>read(message, "$.Subject"))
                .isEqualTo("Your tickets are confirmed · " + bookingRef);
        assertThat(JsonPath.<String>read(message, "$.Text")).contains("Hi Asha", bookingRef, "A1, A2");
        assertThat(sentChannels(UUID.fromString(bookingId))).containsExactlyInAnyOrder("EMAIL", "SMS");
        assertThat(jdbc.sql("SELECT email FROM app_user WHERE id = ?").param(customer).query(String.class).single())
                .isEqualTo(email);
    }

    @Test
    void theSameEventDeliveredTwiceSendsOneEmail() {
        knownUser();
        BookingConfirmed confirmed = new BookingConfirmed(UUID.randomUUID(), "BK9DUPE01", customer, "Dune",
                "PVR Phoenix", Instant.parse("2026-10-03T14:00:00Z"), ZoneId.of("Asia/Kolkata"), List.of("A1"), 25960);

        publish(confirmed);
        Eventually.until("the first delivery", WAIT, () -> completedDeliveries(confirmed.bookingId()) == 1);
        publish(confirmed);
        Eventually.until("the second delivery", WAIT, () -> completedDeliveries(confirmed.bookingId()) == 2);

        assertThat(emailsTo(email)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM notification_log WHERE booking_id = ?")
                .param(confirmed.bookingId()).query(Long.class).single()).isEqualTo(2L);     // EMAIL and SMS, once each
        assertThat(JsonPath.<String>read(latestEmailTo(email), "$.Text")).contains("Sat 3 Oct, 7:30 PM");  // in IST
    }

    @Test
    void aLatePaymentRefundExplainsWhatHappened() {
        knownUser();
        UUID bookingId = UUID.randomUUID();

        publish(new RefundCompleted(UUID.randomUUID(), bookingId, customer, "BK9LATE01", 51920,
                RefundReason.LATE_PAYMENT));

        Eventually.until("the refund email", WAIT, () -> emailsTo(email) == 1);
        String message = latestEmailTo(email);
        assertThat(JsonPath.<String>read(message, "$.Subject")).isEqualTo("Your refund for BK9LATE01 is on its way");
        assertThat(JsonPath.<String>read(message, "$.Text"))
                .contains("came through after the seats had", "₹519.20");
    }

    @Test
    void aPartialCancellationSaysWhichSeatsWentAndWhatComesBack() {
        knownUser();

        UUID bookingId = UUID.randomUUID();
        publish(new BookingCancelled(bookingId, "BK9CANC01", customer, UUID.randomUUID(),
                CancellationReason.CUSTOMER, "Dune", List.of("A1"), 23600, false));

        Eventually.until("the cancellation email", WAIT, () -> emailsTo(email) == 1);
        String message = latestEmailTo(email);
        assertThat(JsonPath.<String>read(message, "$.Subject")).isEqualTo("Booking BK9CANC01 cancelled");
        assertThat(JsonPath.<String>read(message, "$.Text"))
                .contains("seats *A1* of booking", "other seats are still booked", "*₹236* is on its way")   // bold
                .doesNotContain("no refund is due");
        Eventually.until("the SMS", WAIT, () -> sentChannels(bookingId).size() == 2);
    }

    private MockHttpServletRequestBuilder asAsha(MockHttpServletRequestBuilder request) {
        return request.contentType("application/json")
                .header("X-User-Id", customer)
                .header("X-User-Role", "CUSTOMER")
                .header("X-User-Name", "Asha")
                .header("X-User-Email", email)
                .header("X-User-Phone", "+919800000001")
                .header("Idempotency-Key", UUID.randomUUID());
    }

    private void knownUser() {
        jdbc.sql("""
                        INSERT INTO app_user (id, name, email, phone, role)
                        VALUES (?, 'Asha', ?, '+919800000001', 'CUSTOMER')""")
                .params(customer, email).update();
    }

    private void publish(Object event) {
        tx.executeWithoutResult(status -> events.publishEvent(event));
    }

    private long completedDeliveries(UUID bookingId) {
        return jdbc.sql("""
                        SELECT count(*) FROM event_publication
                        WHERE listener_id LIKE '%NotificationListener%' AND completion_date IS NOT NULL
                          AND serialized_event LIKE ?""")
                .param("%" + bookingId + "%").query(Long.class).single();
    }

    private List<String> sentChannels(UUID bookingId) {
        return jdbc.sql("SELECT channel FROM notification_log WHERE booking_id = ? AND status = 'SENT'")
                .param(bookingId).query(String.class).list();
    }

    private int emailsTo(String address) {
        String result = mailpit.get().uri("/api/v1/search?query={query}", "to:" + address)
                .retrieve().body(String.class);
        return JsonPath.read(result, "$.messages_count");
    }

    private String latestEmailTo(String address) {
        String result = mailpit.get().uri("/api/v1/search?query={query}", "to:" + address)
                .retrieve().body(String.class);
        String id = JsonPath.read(result, "$.messages[0].ID");
        return mailpit.get().uri("/api/v1/message/{id}", id).retrieve().body(String.class);
    }
}
