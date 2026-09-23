package com.sumit.movieticketbookingsystem.show;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.catalog.CatalogFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BrowseApiIT {

    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private CatalogFixtures catalog;
    private long cityId;
    private long movieId;
    private LocalDate day;          // the listing date the shows below belong to
    private long morningShow;       // Zeta Cinemas 10:00 HI 2D
    private long eveningShow;       // Zeta Cinemas 18:00 EN 3D

    /*
     * Alpha Talkies: 21:00 HI 2D, and 00:30 the next morning HI 2D (listed under the same day)
     * Zeta Cinemas:  10:00 HI 2D, 18:00 EN 3D
     */
    @BeforeEach
    void setUp() throws Exception {
        catalog = new CatalogFixtures(mvc);
        cityId = catalog.city();
        movieId = catalog.movie(120);
        day = LocalDate.now(IST).plusDays(2);

        long alphaScreen = catalog.screenWithActiveLayout(catalog.pricedTheater(cityId, "Alpha Talkies"));
        long zetaScreen = catalog.screenWithActiveLayout(catalog.pricedTheater(cityId, "Zeta Cinemas"));
        openShow(alphaScreen, at(day, 21, 0), "HI", "2D");
        openShow(alphaScreen, at(day.plusDays(1), 0, 30), "HI", "2D");
        morningShow = openShow(zetaScreen, at(day, 10, 0), "HI", "2D");
        eveningShow = openShow(zetaScreen, at(day, 18, 0), "EN", "3D");
    }

    @Test
    void showtimesAreGroupedByTheaterAndSorted() throws Exception {
        showtimes("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movie.durationMin").value(120))
                .andExpect(jsonPath("$.theaters[*].theater.name").value(contains("Alpha Talkies", "Zeta Cinemas")))
                .andExpect(jsonPath("$.theaters[0].shows[*].startTime").value(contains(
                        iso(at(day, 21, 0)), iso(at(day.plusDays(1), 0, 30)))))
                .andExpect(jsonPath("$.theaters[1].shows", hasSize(2)))
                .andExpect(jsonPath("$.theaters[1].shows[0].priceFromPaise").value(20000))
                .andExpect(jsonPath("$.theaters[1].shows[0].seatsLeft").value(10))
                .andExpect(jsonPath("$.theaters[1].shows[0].availability").value("AVAILABLE"));
    }

    @Test
    void slotsAreCombinedAndEmptyTheatersDropOut() throws Exception {
        showtimes("&slot=MORNING")
                .andExpect(jsonPath("$.theaters[*].theater.name").value(contains("Zeta Cinemas")))
                .andExpect(jsonPath("$.theaters[0].shows[*].showId").value(contains((int) morningShow)));

        showtimes("&slot=EVENING,NIGHT")
                .andExpect(jsonPath("$.theaters[0].shows", hasSize(2)))            // Alpha: 21:00 and 00:30
                .andExpect(jsonPath("$.theaters[1].shows[*].showId").value(contains((int) eveningShow)));
    }

    @Test
    void languageAndFormatFiltersIgnoreCase() throws Exception {
        showtimes("&language=en&format=3d")
                .andExpect(jsonPath("$.theaters", hasSize(1)))
                .andExpect(jsonPath("$.theaters[0].shows[*].showId").value(contains((int) eveningShow)));
        showtimes("&language=TA")
                .andExpect(jsonPath("$.theaters").value(empty()));
    }

    @Test
    void availabilityBadgeFollowsSeatsLeft() throws Exception {
        blockSeats(morningShow, 9);        // 1 of 10 left, under 20%
        blockSeats(eveningShow, 10);

        showtimes("&slot=MORNING,EVENING")
                .andExpect(jsonPath("$.theaters[0].shows[0].seatsLeft").value(1))
                .andExpect(jsonPath("$.theaters[0].shows[0].availability").value("FILLING_FAST"))
                .andExpect(jsonPath("$.theaters[0].shows[1].availability").value("SOLD_OUT"));
    }

    @Test
    void moviesAndDatesOnlyCountOpenShows() throws Exception {
        long unopenedMovie = catalog.movie(90);
        long screen = catalog.screenWithActiveLayout(catalog.pricedTheater(cityId, "Scheduled Only"));
        catalog.create("/api/v1/admin/shows", showJson(unopenedMovie, screen, at(day, 12, 0), "HI", "2D"));

        mvc.perform(asCustomer(get("/api/v1/cities/{id}/movies", cityId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains((int) movieId)));
        mvc.perform(asCustomer(get("/api/v1/movies/{id}/dates", movieId)).param("cityId", String.valueOf(cityId)))
                .andExpect(jsonPath("$").value(contains(day.toString())));   // the 00:30 show counts as this day too
    }

    @Test
    void showsAboutToStartAreHidden() throws Exception {
        long screen = catalog.screenWithActiveLayout(catalog.pricedTheater(cityId, "Last Minute"));
        OffsetDateTime inFiveMinutes = OffsetDateTime.now(IST).plusMinutes(5);
        long soonShow = openShow(screen, inFiveMinutes, "HI", "2D");
        LocalDate listingDate = inFiveMinutes.toLocalTime().isBefore(LocalTime.of(3, 0))
                ? inFiveMinutes.toLocalDate().minusDays(1)
                : inFiveMinutes.toLocalDate();

        mvc.perform(asCustomer(get("/api/v1/movies/{id}/shows", movieId))
                        .param("cityId", String.valueOf(cityId))
                        .param("date", listingDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theaters[*].shows[*].showId").value(not(hasItem((int) soonShow))));
    }

    @Test
    void badRequests() throws Exception {
        showtimes("&slot=BRUNCH").andExpect(status().isBadRequest());
        mvc.perform(asCustomer(get("/api/v1/cities/{id}/movies", Long.MAX_VALUE))).andExpect(status().isNotFound());
    }

    private ResultActions showtimes(String extraParams) throws Exception {
        return mvc.perform(asCustomer(get("/api/v1/movies/" + movieId + "/shows?cityId=" + cityId + "&date=" + day
                + extraParams)));
    }

    private long openShow(long screenId, OffsetDateTime start, String language, String format) throws Exception {
        long showId = catalog.create("/api/v1/admin/shows", showJson(movieId, screenId, start, language, format));
        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/open", showId))).andExpect(status().isOk());
        return showId;
    }

    private void blockSeats(long showId, int count) throws Exception {
        List<Long> seats = jdbc.sql("SELECT layout_seat_id FROM show_seat WHERE show_id = ? LIMIT ?")
                .params(showId, count).query(Long.class).list();
        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/seats/block", showId))
                        .content("{\"seatIds\": " + seats + "}"))
                .andExpect(status().isOk());
    }

    private static String showJson(long movie, long screen, OffsetDateTime start, String language, String format) {
        return """
                {"movieId": %d, "screenId": %d, "startTime": "%s", "language": "%s", "format": "%s"}
                """.formatted(movie, screen, start, language, format);
    }

    // how Jackson writes it: seconds included, unlike OffsetDateTime.toString()
    private static String iso(OffsetDateTime time) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time);
    }

    private static OffsetDateTime at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atOffset(IST);
    }
}
