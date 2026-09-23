package com.sumit.movieticketbookingsystem.show.internal.service;

import com.sumit.movieticketbookingsystem.catalog.CatalogApi;
import com.sumit.movieticketbookingsystem.catalog.CitySummary;
import com.sumit.movieticketbookingsystem.catalog.MovieInfo;
import com.sumit.movieticketbookingsystem.catalog.TheaterSummary;
import com.sumit.movieticketbookingsystem.inventory.SeatAvailabilityReader;
import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.TimeWindow;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.show.internal.domain.TimeSlot;
import com.sumit.movieticketbookingsystem.show.internal.query.ShowQueryService;
import com.sumit.movieticketbookingsystem.show.internal.query.ShowQueryService.ShowRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The movie-first browse flow: city → movies showing → a movie's dates → theaters with their showtimes.
 */
@Service
@Transactional(readOnly = true)
public class BrowseService {

    private final ShowQueryService search;
    private final CatalogApi catalog;
    private final SeatAvailabilityReader availability;
    private final ShowDateResolver showDateResolver;
    private final SlotResolver slotResolver;
    private final BookingProperties properties;
    private final Clock clock;

    BrowseService(ShowQueryService search, CatalogApi catalog, SeatAvailabilityReader availability,
            ShowDateResolver showDateResolver, SlotResolver slotResolver, BookingProperties properties, Clock clock) {
        this.search = search;
        this.catalog = catalog;
        this.availability = availability;
        this.showDateResolver = showDateResolver;
        this.slotResolver = slotResolver;
        this.properties = properties;
        this.clock = clock;
    }

    public List<Movie> moviesShowing(long cityId) {
        DateStrip strip = dateStrip(activeCity(cityId));
        List<Long> movieIds = search.moviesShowing(cityId, strip.first(), strip.last(), bookableAfter());
        return catalog.movies(movieIds).values().stream()
                .map(Movie::from)
                .sorted(Comparator.comparing(Movie::title))
                .toList();
    }

    public List<LocalDate> dates(long movieId, long cityId) {
        DateStrip strip = dateStrip(activeCity(cityId));
        return search.dates(cityId, movieId, strip.first(), strip.last(), bookableAfter());
    }

    /**
     * @param slots    empty means any time of day; several are OR-ed
     * @param language null means any
     * @param format   null means any
     */
    public Showtimes showtimes(long movieId, long cityId, LocalDate date, Set<TimeSlot> slots, String language,
            String format) {
        CitySummary city = activeCity(cityId);
        Movie movie = Movie.from(catalog.movie(movieId));
        Instant bookableAfter = bookableAfter();
        List<TimeWindow> windows = slots.stream().map(slot -> slotResolver.window(slot, date, city.zone())).toList();

        Predicate<ShowRow> wanted = row -> row.startTime().isAfter(bookableAfter)
                && (windows.isEmpty() || windows.stream().anyMatch(window -> window.contains(row.startTime())))
                && (language == null || row.language().equalsIgnoreCase(language))
                && (format == null || row.format().equalsIgnoreCase(format));
        List<ShowRow> rows = search.showsForDay(cityId, movieId, date).stream().filter(wanted).toList();

        Map<Long, Integer> seatsLeft = availability.seatsLeft(rows.stream().map(ShowRow::showId).toList());
        Map<Long, List<Showtime>> byTheater = new LinkedHashMap<>();
        for (ShowRow row : rows) {
            int left = seatsLeft.getOrDefault(row.showId(), 0);
            byTheater.computeIfAbsent(row.theaterId(), id -> new ArrayList<>())
                    .add(new Showtime(row.showId(), row.startTime().atZone(city.zone()).toOffsetDateTime(),
                            row.language(), row.format(), row.priceFromPaise(), left,
                            Availability.of(left, row.totalSeats(), properties.fillingFastPercent())));
        }

        Map<Long, TheaterSummary> theaters = catalog.theaters(byTheater.keySet());
        List<TheaterShowtimes> result = byTheater.entrySet().stream()
                .map(entry -> new TheaterShowtimes(theaters.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(theater -> theater.theater().name()))
                .toList();
        return new Showtimes(movie, date, result);
    }

    private CitySummary activeCity(long cityId) {
        CitySummary city = catalog.city(cityId);
        if (!city.active()) {
            throw new NotFoundException("City", cityId);
        }
        return city;
    }

    private DateStrip dateStrip(CitySummary city) {
        LocalDate today = showDateResolver.listingDate(Instant.now(clock), city.zone());
        return new DateStrip(today, today.plusDays(properties.dateStripDays() - 1L));
    }

    private Instant bookableAfter() {
        return Instant.now(clock).plus(properties.bookingCutoff());
    }

    private record DateStrip(LocalDate first, LocalDate last) {
    }

    public record Movie(long id, String title, long durationMin, String certification) {

        static Movie from(MovieInfo info) {
            return new Movie(info.movieId(), info.title(), info.duration().toMinutes(), info.certification());
        }
    }

    public record Showtimes(Movie movie, LocalDate date, List<TheaterShowtimes> theaters) {
    }

    public record TheaterShowtimes(TheaterSummary theater, List<Showtime> shows) {
    }

    public record Showtime(long showId, OffsetDateTime startTime, String language, String format,
                           Long priceFromPaise, int seatsLeft, Availability availability) {
    }

    public enum Availability {
        AVAILABLE,
        FILLING_FAST,
        SOLD_OUT;

        static Availability of(int seatsLeft, int totalSeats, int fillingFastPercent) {
            if (seatsLeft == 0) {
                return SOLD_OUT;
            }
            return seatsLeft * 100 < totalSeats * fillingFastPercent ? FILLING_FAST : AVAILABLE;
        }
    }
}
