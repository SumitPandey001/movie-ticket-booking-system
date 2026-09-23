package com.sumit.movieticketbookingsystem.booking.internal.service;

import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingStatus;
import com.sumit.movieticketbookingsystem.booking.internal.domain.Cancellation;
import com.sumit.movieticketbookingsystem.booking.internal.persistence.BookingRepository;
import com.sumit.movieticketbookingsystem.catalog.CatalogApi;
import com.sumit.movieticketbookingsystem.catalog.MovieInfo;
import com.sumit.movieticketbookingsystem.catalog.TheaterSummary;
import com.sumit.movieticketbookingsystem.payment.PaymentApi;
import com.sumit.movieticketbookingsystem.payment.PaymentSummary;
import com.sumit.movieticketbookingsystem.show.ShowApi;
import com.sumit.movieticketbookingsystem.show.ShowDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** A customer's bookings, as they see them: history lists and the details page. Read-only. */
@Service
@Transactional(readOnly = true)
public class BookingQueryService {

    private static final Sort SOONEST_FIRST = Sort.by("showStartTime", "id");
    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "showStartTime", "id");

    private final BookingRepository bookings;
    private final ShowApi shows;
    private final CatalogApi catalog;
    private final PaymentApi payments;
    private final Clock clock;

    BookingQueryService(BookingRepository bookings, ShowApi shows, CatalogApi catalog, PaymentApi payments,
            Clock clock) {
        this.bookings = bookings;
        this.shows = shows;
        this.catalog = catalog;
        this.payments = payments;
        this.clock = clock;
    }

    /**
     * UPCOMING and PAST are confirmed bookings (partly cancelled ones included) before and after the show starts;
     * CANCELLED is fully cancelled ones. Holds that never became bookings aren't history.
     */
    public enum View {
        UPCOMING,
        PAST,
        CANCELLED
    }

    public record ShowInfo(String movieTitle, String theaterName) {
    }

    /** @param seats read here, like {@link BookingDetails#cancellations}; one query loads a whole page's seats */
    public record BookingSummary(Booking booking, ShowInfo show, List<BookingSeat> seats) {
    }

    /**
     * @param cancellations oldest first; read here because the booking's own list isn't loaded outside this service
     * @param payment       null until the booking is paid for
     */
    public record BookingDetails(Booking booking, ShowInfo show, List<Cancellation> cancellations,
                                 PaymentSummary payment) {
    }

    public Page<BookingSummary> history(UUID userId, View view, int page, int size) {
        Instant now = Instant.now(clock);
        Page<Booking> found = switch (view) {
            case UPCOMING -> bookings.findByUserIdAndStatusAndShowStartTimeAfter(userId, BookingStatus.CONFIRMED,
                    now, PageRequest.of(page, size, SOONEST_FIRST));
            case PAST -> bookings.findByUserIdAndStatusAndShowStartTimeLessThanEqual(userId, BookingStatus.CONFIRMED,
                    now, PageRequest.of(page, size, LATEST_FIRST));
            case CANCELLED -> bookings.findByUserIdAndStatus(userId, BookingStatus.CANCELLED,
                    PageRequest.of(page, size, LATEST_FIRST));
        };
        Map<Long, ShowInfo> showInfo = showInfo(found.map(Booking::getShowId).toSet());
        return found.map(booking -> new BookingSummary(booking, showInfo.get(booking.getShowId()),
                booking.getSeats()));
    }

    public BookingDetails details(UUID bookingId, UUID userId) {
        Booking booking = bookings.findOwn(bookingId, userId);
        return new BookingDetails(booking, showInfo(Set.of(booking.getShowId())).get(booking.getShowId()),
                booking.getCancellations(), payments.summary(bookingId).orElse(null));
    }

    private Map<Long, ShowInfo> showInfo(Set<Long> showIds) {
        Map<Long, ShowDetails> details = shows.shows(showIds);
        Map<Long, MovieInfo> movies = catalog.movies(
                details.values().stream().map(ShowDetails::movieId).collect(Collectors.toSet()));
        Map<Long, TheaterSummary> theaters = catalog.theaters(
                details.values().stream().map(ShowDetails::theaterId).collect(Collectors.toSet()));
        return details.values().stream().collect(Collectors.toMap(ShowDetails::showId, show -> new ShowInfo(
                movies.get(show.movieId()).title(), theaters.get(show.theaterId()).name())));
    }
}
