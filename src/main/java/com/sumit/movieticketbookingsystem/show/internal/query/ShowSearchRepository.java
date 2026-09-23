package com.sumit.movieticketbookingsystem.show.internal.query;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Read-only queries behind the browse pages. All of them are served by show_browse_idx.
 * Instants go to the driver as UTC OffsetDateTime, which it maps to timestamptz.
 */
@Repository
public class ShowSearchRepository {

    private final JdbcClient jdbc;

    ShowSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Movies with at least one bookable show in the city between the two listing dates. */
    public List<Long> moviesShowing(long cityId, LocalDate from, LocalDate to, Instant bookableAfter) {
        return jdbc.sql("""
                        SELECT DISTINCT movie_id FROM show
                        WHERE city_id = :cityId AND status = 'OPEN' AND show_date BETWEEN :from AND :to
                          AND start_time > :bookableAfter
                        """)
                .param("cityId", cityId)
                .param("from", from)
                .param("to", to)
                .param("bookableAfter", bookableAfter.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .list();
    }

    public List<LocalDate> dates(long cityId, long movieId, LocalDate from, LocalDate to, Instant bookableAfter) {
        return jdbc.sql("""
                        SELECT DISTINCT show_date FROM show
                        WHERE city_id = :cityId AND movie_id = :movieId AND status = 'OPEN'
                          AND show_date BETWEEN :from AND :to AND start_time > :bookableAfter
                        ORDER BY show_date
                        """)
                .param("cityId", cityId)
                .param("movieId", movieId)
                .param("from", from)
                .param("to", to)
                .param("bookableAfter", bookableAfter.atOffset(ZoneOffset.UTC))
                .query(LocalDate.class)
                .list();
    }

    /**
     * Every open show of a movie in a city for one listing date, unfiltered. Not filtered by time either,
     * so the result stays valid all day (it's what gets cached later); callers apply the booking cutoff.
     */
    public List<ShowRow> showsForDay(long cityId, long movieId, LocalDate date) {
        return jdbc.sql("""
                        SELECT id, theater_id, start_time, language, format, price_from_paise, total_seats
                        FROM show
                        WHERE city_id = :cityId AND movie_id = :movieId AND show_date = :date AND status = 'OPEN'
                        ORDER BY theater_id, start_time
                        """)
                .param("cityId", cityId)
                .param("movieId", movieId)
                .param("date", date)
                .query((rs, row) -> new ShowRow(
                        rs.getLong("id"),
                        rs.getLong("theater_id"),
                        rs.getObject("start_time", OffsetDateTime.class).toInstant(),
                        rs.getString("language"),
                        rs.getString("format"),
                        rs.getObject("price_from_paise", Long.class),
                        rs.getInt("total_seats")))
                .list();
    }

    public record ShowRow(long showId, long theaterId, Instant startTime, String language, String format,
                          Long priceFromPaise, int totalSeats) {
    }
}
