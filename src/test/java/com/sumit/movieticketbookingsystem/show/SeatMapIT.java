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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SeatMapIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private CatalogFixtures catalog;
    private long screenId;
    private long movieId;
    private OffsetDateTime start;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new CatalogFixtures(mvc);
        screenId = catalog.screen();
        long layoutId = catalog.create("/api/v1/admin/screens/" + screenId + "/layouts", """
                {"rows": [{"label": "A", "segments": [{"from": 1, "to": 3, "category": "REGULAR"}, {"aisle": 1},
                                                      {"from": 4, "to": 6, "category": "REGULAR"}]},
                          {"label": "B", "segments": [{"from": 1, "to": 4, "category": "RECLINER"}]}],
                 "blocked": ["A3"], "wheelchair": ["B1", "B2"]}
                """);
        mvc.perform(asAdmin(post("/api/v1/admin/layouts/{id}/activate", layoutId))).andExpect(status().isOk());
        movieId = catalog.movie(140);
        start = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).plusDays(5).truncatedTo(ChronoUnit.DAYS).withHour(20);
    }

    @Test
    void combinesLayoutStatusesAndPrices() throws Exception {
        long showId = openShow("""
                {"RECLINER": 45000}
                """);
        long a1 = seatId(showId, "A1");
        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/seats/block", showId))
                        .content("{\"seatIds\": [" + a1 + "]}"))
                .andExpect(status().isOk());

        mvc.perform(asCustomer(get("/api/v1/shows/{id}/seats", showId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("Audi 1"))
                .andExpect(jsonPath("$.gridRows").value(2))
                .andExpect(jsonPath("$.gridCols").value(7))
                .andExpect(jsonPath("$.categories[*].code").value(contains("REGULAR", "RECLINER")))
                .andExpect(jsonPath("$.categories[*].pricePaise").value(contains(20000, 45000)))
                .andExpect(jsonPath("$.seats", hasSize(10)))
                // A1: blocked by the admin for this show
                .andExpect(jsonPath("$.seats[0].label").value("A1"))
                .andExpect(jsonPath("$.seats[0].status").value("BLOCKED"))
                // A3: blocked in the layout
                .andExpect(jsonPath("$.seats[2].type").value("BLOCKED"))
                .andExpect(jsonPath("$.seats[2].status").value("BLOCKED"))
                // A4 sits after the one-column aisle
                .andExpect(jsonPath("$.seats[3].label").value("A4"))
                .andExpect(jsonPath("$.seats[3].col").value(5))
                .andExpect(jsonPath("$.seats[3].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.seats[6].label").value("B1"))
                .andExpect(jsonPath("$.seats[6].row").value(2))
                .andExpect(jsonPath("$.seats[6].type").value("WHEELCHAIR"));
    }

    @Test
    void onlyOpenShowsHaveASeatMap() throws Exception {
        long scheduled = catalog.create("/api/v1/admin/shows", showJson("null"));

        mvc.perform(asCustomer(get("/api/v1/shows/{id}/seats", scheduled)))
                .andExpect(status().isNotFound());
        mvc.perform(asCustomer(get("/api/v1/shows/{id}/seats", Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
    }

    private long openShow(String priceOverrides) throws Exception {
        long showId = catalog.create("/api/v1/admin/shows", showJson(priceOverrides));
        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/open", showId))).andExpect(status().isOk());
        return showId;
    }

    private String showJson(String priceOverrides) {
        return """
                {"movieId": %d, "screenId": %d, "startTime": "%s", "language": "HI", "format": "2D",
                 "priceOverrides": %s}
                """.formatted(movieId, screenId, start, priceOverrides);
    }

    private long seatId(long showId, String label) {
        return jdbc.sql("SELECT layout_seat_id FROM show_seat WHERE show_id = ? AND seat_label = ?")
                .params(showId, label).query(Long.class).single();
    }
}
