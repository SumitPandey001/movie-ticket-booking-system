package com.sumit.movieticketbookingsystem.show;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.catalog.CatalogFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rows are changed with plain SQL here on purpose: that skips the events, so the page keeps serving what's in
 * Redis until a change made through the API clears it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BrowseCacheIT {

    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private CatalogFixtures catalog;
    private long cityId;
    private long movieId;
    private long screenId;
    private LocalDate day;
    private long showId;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new CatalogFixtures(mvc);
        cityId = catalog.city();
        movieId = catalog.movie(100);
        screenId = catalog.screenWithActiveLayout(catalog.pricedTheater(cityId, "Cached Cinema"));
        day = LocalDate.now(IST).plusDays(3);
        showId = openShow(day.atTime(11, 0).atOffset(IST));
    }

    @Test
    void dayIsServedFromRedisUntilAListingChange() throws Exception {
        showtimes().andExpect(jsonPath("$.theaters[0].shows[0].language").value("HI"));
        assertThat(redis.hasKey("show-day:" + cityId + ":" + movieId + ":" + day)).isTrue();

        jdbc.sql("UPDATE show SET language = 'TA' WHERE id = ?").param(showId).update();
        showtimes().andExpect(jsonPath("$.theaters[0].shows[0].language").value("HI"));   // still cached

        openShow(day.atTime(15, 0).atOffset(IST));                                         // evicts the day
        showtimes()
                .andExpect(jsonPath("$.theaters[0].shows.length()").value(2))
                .andExpect(jsonPath("$.theaters[0].shows[0].language").value("TA"));
    }

    @Test
    void seatsLeftComesFromTheCounterUntilSeatsChange() throws Exception {
        showtimes().andExpect(jsonPath("$.theaters[0].shows[0].seatsLeft").value(10));
        assertThat(redis.opsForValue().get("seats-left:" + showId)).isEqualTo("10");

        List<Long> seats = jdbc.sql("SELECT layout_seat_id FROM show_seat WHERE show_id = ? ORDER BY layout_seat_id")
                .param(showId).query(Long.class).list();
        jdbc.sql("UPDATE show_seat SET status = 'BLOCKED' WHERE show_id = ? AND layout_seat_id IN (?, ?, ?)")
                .params(showId, seats.get(0), seats.get(1), seats.get(2)).update();
        showtimes().andExpect(jsonPath("$.theaters[0].shows[0].seatsLeft").value(10));      // counter not touched

        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/seats/block", showId))
                        .content("{\"seatIds\": [" + seats.get(3) + "]}"))
                .andExpect(status().isOk());
        showtimes().andExpect(jsonPath("$.theaters[0].shows[0].seatsLeft").value(6));       // rebuilt: 10 - 3 - 1
    }

    @Test
    void failedChangeLeavesTheCacheAlone() throws Exception {
        showtimes();
        String key = "show-day:" + cityId + ":" + movieId + ":" + day;

        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/open", showId)))   // already open: rolled back
                .andExpect(status().isConflict());
        assertThat(redis.hasKey(key)).isTrue();
    }

    private ResultActions showtimes() throws Exception {
        return mvc.perform(asCustomer(get("/api/v1/movies/{id}/shows", movieId))
                        .param("cityId", String.valueOf(cityId))
                        .param("date", day.toString()))
                .andExpect(status().isOk());
    }

    private long openShow(OffsetDateTime start) throws Exception {
        long id = catalog.create("/api/v1/admin/shows", """
                {"movieId": %d, "screenId": %d, "startTime": "%s", "language": "HI", "format": "2D"}
                """.formatted(movieId, screenId, start));
        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/open", id))).andExpect(status().isOk());
        return id;
    }
}
