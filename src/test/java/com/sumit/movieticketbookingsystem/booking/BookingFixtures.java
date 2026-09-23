package com.sumit.movieticketbookingsystem.booking;

import com.sumit.movieticketbookingsystem.catalog.CatalogFixtures;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shows ready to book: scheduled two days out on a fresh screen with A1-A5 regular (₹200) and B1-B5 premium (₹300).
 */
public class BookingFixtures {

    private final MockMvc mvc;
    private final JdbcClient jdbc;
    private final CatalogFixtures catalog;

    public BookingFixtures(MockMvc mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.catalog = new CatalogFixtures(mvc);
    }

    public BookableShow openShow() throws Exception {
        BookableShow show = scheduledShow();
        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/open", show.id()))).andExpect(status().isOk());
        return show;
    }

    /** Created but never opened, so not bookable. */
    public BookableShow scheduledShow() throws Exception {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30))
                .plusDays(2).truncatedTo(ChronoUnit.DAYS).withHour(19);
        long showId = catalog.create("/api/v1/admin/shows", """
                {"movieId": %d, "screenId": %d, "startTime": "%s", "language": "HI", "format": "2D"}
                """.formatted(catalog.movie(120), catalog.screenWithActiveLayout(), start));
        Map<String, Long> seats = jdbc.sql("SELECT seat_label, layout_seat_id FROM show_seat WHERE show_id = ?")
                .param(showId)
                .query((rs, row) -> Map.entry(rs.getString(1), rs.getLong(2)))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new BookableShow(showId, seats);
    }

    public record BookableShow(long id, Map<String, Long> seatIdsByLabel) {

        public Set<Long> seats(String... labels) {
            return Arrays.stream(labels).map(seatIdsByLabel::get).collect(Collectors.toSet());
        }
    }
}
