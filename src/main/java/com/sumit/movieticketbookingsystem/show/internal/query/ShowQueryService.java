package com.sumit.movieticketbookingsystem.show.internal.query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Reads behind the browse pages. {@link ShowSearchRepository} answers from Postgres;
 * {@link CachingShowQueryService} puts Redis in front of the busiest query.
 */
public interface ShowQueryService {

    /** Movies with at least one bookable show in the city between the two listing dates. */
    List<Long> moviesShowing(long cityId, LocalDate from, LocalDate to, Instant bookableAfter);

    List<LocalDate> dates(long cityId, long movieId, LocalDate from, LocalDate to, Instant bookableAfter);

    /**
     * Every open show of a movie in a city for one listing date, unfiltered. Not filtered by time either,
     * so the result stays valid all day and can be cached; callers apply the booking cutoff.
     */
    List<ShowRow> showsForDay(long cityId, long movieId, LocalDate date);

    record ShowRow(long showId, long theaterId, Instant startTime, String language, String format,
                   Long priceFromPaise, int totalSeats) {
    }
}
