package com.sumit.movieticketbookingsystem.booking;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IdempotencyIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private BookableShow show;
    private UUID customer;

    @BeforeEach
    void setUp() throws Exception {
        show = new BookingFixtures(mvc, jdbc).openShow();
        customer = UUID.randomUUID();
    }

    @Test
    void retryWithTheSameKeyReplaysTheFirstAnswer() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = hold(customer, key, "A1", "A2").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String retry = hold(customer, key, "A1", "A2").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(retry).isEqualTo(first);
        assertThat(bookingsForShow()).isEqualTo(1);
    }

    @Test
    void sameKeyWithADifferentBodyIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        hold(customer, key, "A3").andExpect(status().isCreated());

        hold(customer, key, "A4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void businessErrorsAreReplayedToo() throws Exception {
        hold(UUID.randomUUID(), UUID.randomUUID().toString(), "B1").andExpect(status().isCreated());
        String key = UUID.randomUUID().toString();
        hold(customer, key, "B1", "B2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEATS_UNAVAILABLE"));

        // even once B1 is free again, the same key keeps answering what it answered the first time
        jdbc.sql("UPDATE show_seat SET status = 'AVAILABLE', booking_id = NULL, hold_expires_at = NULL "
                + "WHERE show_id = ? AND status = 'HELD'").param(show.id()).update();
        hold(customer, key, "B1", "B2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEATS_UNAVAILABLE"))
                .andExpect(jsonPath("$.unavailableSeatIds[0]").value(show.seatIdsByLabel().get("B1").intValue()));
    }

    @Test
    void keysBelongToOneCustomer() throws Exception {
        String key = UUID.randomUUID().toString();
        hold(customer, key, "A5").andExpect(status().isCreated());

        hold(UUID.randomUUID(), key, "B5").andExpect(status().isCreated());
    }

    @Test
    void aKeyStillBeingProcessedIsRefused() throws Exception {
        String key = UUID.randomUUID().toString();
        hold(customer, key, "B3").andExpect(status().isCreated());
        // as if the first request were still running: same key, same request, no saved answer yet
        jdbc.sql("UPDATE idempotency_record SET status = 'IN_PROGRESS' WHERE user_id = ? AND idem_key = ?")
                .params(customer, key).update();

        hold(customer, key, "B3")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_IN_PROGRESS"));
    }

    @Test
    void anExpiredKeyCanBeUsedAgain() throws Exception {
        String key = UUID.randomUUID().toString();
        insertRecord(key, "COMPLETED", Instant.now().minusSeconds(60));

        hold(customer, key, "B4").andExpect(status().isCreated());
    }

    @Test
    void anUnexpectedFailureForgetsTheKeySoTheRetryRuns() throws Exception {
        List<Map<String, Object>> prices = jdbc.sql("SELECT * FROM show_category_price WHERE show_id = ?")
                .param(show.id()).query().listOfRows();
        // pricing now blows up
        jdbc.sql("DELETE FROM show_category_price WHERE show_id = ?").param(show.id()).update();
        String key = UUID.randomUUID().toString();
        hold(customer, key, "A2").andExpect(status().isInternalServerError());

        prices.forEach(row -> jdbc.sql("""
                        INSERT INTO show_category_price (show_id, category_id, price_paise, overridden)
                        VALUES (:show_id, :category_id, :price_paise, :overridden)
                        """)
                .params(row).update());
        hold(customer, key, "A2").andExpect(status().isCreated());
    }

    @Test
    void theKeyIsRequired() throws Exception {
        mvc.perform(asCustomer(post("/api/v1/bookings"), customer).content("""
                        {"showId": %d, "seatIds": %s}
                        """.formatted(show.id(), show.seats("A1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("An Idempotency-Key header of up to 80 characters is required"));
        assertThat(bookingsForShow()).isZero();
    }

    private ResultActions hold(UUID user, String key, String... labels) throws Exception {
        return mvc.perform(asCustomer(post("/api/v1/bookings"), user)
                .header("Idempotency-Key", key)
                .content("""
                        {"showId": %d, "seatIds": %s}
                        """.formatted(show.id(), show.seats(labels).stream().sorted().toList())));
    }

    // a leftover from some older, different request
    private void insertRecord(String key, String status, Instant expiresAt) {
        jdbc.sql("""
                        INSERT INTO idempotency_record (user_id, idem_key, request_hash, status, created_at, expires_at)
                        VALUES (?, ?, ?, ?, now(), ?)
                        """)
                .params(customer, key, "0".repeat(64), status, expiresAt.atOffset(ZoneOffset.UTC))
                .update();
    }

    private long bookingsForShow() {
        return jdbc.sql("SELECT count(*) FROM booking WHERE show_id = ?").param(show.id()).query(Long.class).single();
    }
}
