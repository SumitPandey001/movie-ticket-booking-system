package com.sumit.movieticketbookingsystem.inventory;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.catalog.CatalogFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The seat-claim SQL on its own, against real Postgres. Each hold runs in its own transaction with a minimal
 * booking row, because show_seat.booking_id is a foreign key checked at commit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SeatHoldIT {

    private static final Duration HOLD = Duration.ofMinutes(8);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private InventoryApi inventory;

    @Autowired
    private SeatAvailabilityReader availability;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcClient jdbc;

    private long showId;
    private Instant showStart;
    private Map<String, Long> seats;    // layout seat id by label; the fixture layout has A1-A5, B1-B5

    @BeforeEach
    void createShow() throws Exception {
        CatalogFixtures catalog = new CatalogFixtures(mvc);
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).truncatedTo(ChronoUnit.HOURS);
        showStart = start.toInstant();
        showId = catalog.create("/api/v1/admin/shows", """
                {"movieId": %d, "screenId": %d, "startTime": "%s", "language": "HI", "format": "2D"}
                """.formatted(catalog.movie(120), catalog.screenWithActiveLayout(), start));
        seats = jdbc.sql("SELECT seat_label, layout_seat_id FROM show_seat WHERE show_id = ?")
                .param(showId)
                .query((rs, row) -> Map.entry(rs.getString(1), rs.getLong(2)))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Test
    void claimsEverySeatAndLowersTheCounter() {
        assertThat(seatsLeft()).isEqualTo(10);    // primes the counter
        Instant now = Instant.now();

        List<HeldSeat> held = hold(Set.of(seats.get("A1"), seats.get("A2")), UUID.randomUUID(), now);

        assertThat(held).extracting(HeldSeat::label).containsExactlyInAnyOrder("A1", "A2");
        assertThat(status("A1", now)).isEqualTo(SeatStatus.HELD);
        assertThat(status("A3", now)).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatsLeft()).isEqualTo(8);
    }

    @Test
    void allOrNothing() {
        Instant now = Instant.now();
        hold(Set.of(seats.get("A2")), UUID.randomUUID(), now);

        assertThatThrownBy(() -> hold(Set.of(seats.get("A1"), seats.get("A2")), UUID.randomUUID(), now))
                .isInstanceOfSatisfying(SeatsUnavailableException.class,
                        e -> assertThat(e.seatIds()).containsExactly(seats.get("A2")));
        assertThat(status("A1", now)).isEqualTo(SeatStatus.AVAILABLE);   // rolled back with the rest
    }

    @Test
    void expiredHoldIsFreeForTheNextCustomer() {
        Instant now = Instant.now();
        UUID first = UUID.randomUUID();
        hold(Set.of(seats.get("A1")), first, now);
        assertThat(seatsLeft()).isEqualTo(9);

        Instant later = now.plus(HOLD).plusSeconds(1);
        assertThat(status("A1", later)).isEqualTo(SeatStatus.AVAILABLE);   // lazy expiry, no sweeper involved

        UUID second = UUID.randomUUID();
        hold(Set.of(seats.get("A1")), second, later);
        assertThat(seatsLeft()).isEqualTo(9);                               // not taken off twice
        assertThat(release(first)).isZero();   // no longer first's seat
    }

    @Test
    void releaseFreesOnlyThisBookingsSeats() {
        Instant now = Instant.now();
        UUID mine = UUID.randomUUID();
        hold(Set.of(seats.get("B1"), seats.get("B2")), mine, now);
        hold(Set.of(seats.get("B3")), UUID.randomUUID(), now);

        assertThat(release(mine)).isEqualTo(2);
        assertThat(status("B1", now)).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(status("B3", now)).isEqualTo(SeatStatus.HELD);
        assertThat(seatsLeft()).isEqualTo(9);
    }

    @Test
    void lockedRowsFailFastInsteadOfWaiting() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        CompletableFuture<Void> slowHold = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            UUID bookingId = UUID.randomUUID();
            inventory.hold(showId, Set.of(seats.get("A5")), bookingId, Instant.now().plus(HOLD), Instant.now());
            insertBooking(bookingId);
            locked.countDown();
            await(finish);                                     // keep the row lock
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        long started = System.nanoTime();
        assertThatThrownBy(() -> hold(Set.of(seats.get("A5")), UUID.randomUUID(), Instant.now()))
                .isInstanceOf(SeatsUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));

        finish.countDown();
        slowHold.get(10, TimeUnit.SECONDS);
    }

    @Test
    void seatsThatArentPartOfTheShowCountAsTaken() {
        assertThatThrownBy(() -> hold(Set.of(seats.get("A1"), Long.MAX_VALUE), UUID.randomUUID(), Instant.now()))
                .isInstanceOfSatisfying(SeatsUnavailableException.class,
                        e -> assertThat(e.seatIds()).containsExactly(Long.MAX_VALUE));
    }

    @Test
    void heldSeatsNeedTheirBookingByCommit() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> inventory.hold(showId, Set.of(seats.get("A3")),
                UUID.randomUUID(), Instant.now().plus(HOLD), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("show_seat_booking_fk");
    }

    private List<HeldSeat> hold(Set<Long> seatIds, UUID bookingId, Instant now) {
        return tx.execute(status -> {
            List<HeldSeat> held = inventory.hold(showId, seatIds, bookingId, now.plus(HOLD), now);
            insertBooking(bookingId);
            return held;
        });
    }

    private int release(UUID bookingId) {
        Integer released = tx.execute(status -> inventory.releaseHeld(showId, bookingId));
        return released == null ? 0 : released;
    }

    private void insertBooking(UUID bookingId) {
        jdbc.sql("""
                        INSERT INTO booking (id, booking_ref, user_id, show_id, show_start_time, status, hold_expires_at,
                                             seat_count, subtotal_paise, fee_paise, tax_paise, total_paise, created_at)
                        VALUES (?, ?, ?, ?, ?, 'HELD', ?, 1, 20000, 2000, 3960, 25960, now())
                        """)
                .params(bookingId, bookingId.toString().substring(0, 12), UUID.randomUUID(), showId,
                        showStart.atOffset(ZoneOffset.UTC), showStart.atOffset(ZoneOffset.UTC))
                .update();
    }

    private SeatStatus status(String label, Instant now) {
        return inventory.seatStatuses(showId, now).get(seats.get(label));
    }

    private int seatsLeft() {
        return availability.seatsLeft(List.of(showId)).get(showId);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
