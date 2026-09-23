package com.sumit.movieticketbookingsystem.booking;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingStatus;
import com.sumit.movieticketbookingsystem.booking.internal.domain.PriceTotals;
import com.sumit.movieticketbookingsystem.booking.internal.persistence.BookingRepository;
import com.sumit.movieticketbookingsystem.catalog.CatalogFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BookingRepositoryIT {

    private static final List<BookingSeat> SEATS = List.of(
            new BookingSeat(1, "A1", 1, 20000, 0, 2360, 25960),
            new BookingSeat(2, "A2", 1, 20000, 0, 2360, 25960));
    private static final PriceTotals TOTALS = new PriceTotals(40000, 0, 4000, 7920, 51920);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BookingRepository bookings;

    private long showId;
    private Instant showStart;

    @BeforeEach
    void createShow() throws Exception {
        CatalogFixtures catalog = new CatalogFixtures(mvc);
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30))
                .plusDays(2).truncatedTo(ChronoUnit.DAYS).withHour(18);
        showStart = start.toInstant();
        showId = catalog.create("/api/v1/admin/shows", """
                {"movieId": %d, "screenId": %d, "startTime": "%s", "language": "HI", "format": "2D"}
                """.formatted(catalog.movie(120), catalog.screenWithActiveLayout(), start));
    }

    @Test
    void bookingIsStoredWithItsSeats() {
        UUID userId = UUID.randomUUID();
        Booking saved = bookings.save(hold(userId));

        Booking loaded = bookings.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(loaded.getUserId()).isEqualTo(userId);
        assertThat(loaded.getTotals()).isEqualTo(TOTALS);
        assertThat(loaded.getSeats()).isEqualTo(SEATS);
    }

    @Test
    void oneLiveHoldPerCustomerAndShow() {
        UUID userId = UUID.randomUUID();
        Booking first = bookings.save(hold(userId));

        assertThatThrownBy(() -> bookings.saveAndFlush(hold(userId)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("booking_one_active_hold");

        // once the first hold is closed the customer may hold again
        Booking reloaded = bookings.findById(first.getId()).orElseThrow();
        reloaded.release(Instant.now());
        bookings.save(reloaded);
        bookings.saveAndFlush(hold(userId));
    }

    private Booking hold(UUID userId) {
        Instant now = Instant.now();
        return Booking.hold(UUID.randomUUID(), "BK" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                userId, showId, showStart, now.plusSeconds(480), SEATS, TOTALS, now);
    }
}
