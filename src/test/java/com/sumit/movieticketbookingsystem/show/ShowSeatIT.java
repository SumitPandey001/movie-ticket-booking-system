package com.sumit.movieticketbookingsystem.show;

import com.jayway.jsonpath.JsonPath;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShowSeatIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private long showId;
    private Map<String, Long> seatIds;   // by label

    @BeforeEach
    void createShow() throws Exception {
        CatalogFixtures catalog = new CatalogFixtures(mvc);
        long screenId = catalog.screen();
        long layoutId = catalog.create("/api/v1/admin/screens/" + screenId + "/layouts", """
                {"rows": [{"label": "A", "segments": [{"from": 1, "to": 4, "category": "REGULAR"}]},
                          {"label": "B", "segments": [{"from": 1, "to": 2, "category": "PREMIUM"}]}],
                 "blocked": ["A4"]}
                """);
        mvc.perform(asAdmin(post("/api/v1/admin/layouts/{id}/activate", layoutId))).andExpect(status().isOk());
        seatIds = seatIdsByLabel(screenId);

        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30))
                .plusDays(2).truncatedTo(ChronoUnit.DAYS).withHour(19);
        showId = catalog.create("/api/v1/admin/shows", """
                {"movieId": %d, "screenId": %d, "startTime": "%s", "language": "EN", "format": "2D"}
                """.formatted(catalog.movie(120), screenId, start));
    }

    @Test
    void everyLayoutSeatGetsARowAndLayoutBlocksCarryOver() {
        List<Map<String, Object>> rows = jdbc.sql("""
                        SELECT seat_label, category_id, status FROM show_seat WHERE show_id = ? ORDER BY seat_label
                        """)
                .param(showId).query().listOfRows();

        assertThat(rows).hasSize(6);
        assertThat(rows).extracting(row -> row.get("seat_label"))
                .containsExactly("A1", "A2", "A3", "A4", "B1", "B2");
        assertThat(statusOf("A4")).isEqualTo("BLOCKED");
        assertThat(statusOf("A1")).isEqualTo("AVAILABLE");
        assertThat(rows.get(4)).contains(entry("category_id", premiumCategoryId()));
    }

    @Test
    void blockAndUnblockOnlyChangeSeatsInTheRightState() throws Exception {
        long a1 = seatIds.get("A1");
        long a4 = seatIds.get("A4");

        String blocked = mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/seats/block", showId)).content("""
                        {"seatIds": [%d, %d, %d]}
                        """.formatted(a1, a4, Long.MAX_VALUE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> unchanged = JsonPath.read(blocked, "$.unchangedSeatIds");
        // A4 was already blocked
        assertThat(unchanged).map(Number::longValue).containsExactlyInAnyOrder(a4, Long.MAX_VALUE);
        assertThat(statusOf("A1")).isEqualTo("BLOCKED");

        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/seats/unblock", showId)).content("""
                        {"seatIds": [%d, %d]}
                        """.formatted(a1, a4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unchangedSeatIds").value(empty()));
        assertThat(statusOf("A1")).isEqualTo("AVAILABLE");
        assertThat(statusOf("A4")).isEqualTo("AVAILABLE");
    }

    @Test
    void blockNeedsAShowAndSomeSeats() throws Exception {
        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/seats/block", Long.MAX_VALUE)).content("""
                        {"seatIds": [1]}
                        """))
                .andExpect(status().isNotFound());
        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/seats/block", showId)).content("""
                        {"seatIds": []}
                        """))
                .andExpect(status().isBadRequest());
    }

    private String statusOf(String label) {
        return jdbc.sql("SELECT status FROM show_seat WHERE show_id = ? AND seat_label = ?")
                .params(showId, label).query(String.class).single();
    }

    private long premiumCategoryId() {
        return jdbc.sql("SELECT id FROM seat_category WHERE code = 'PREMIUM'").query(Long.class).single();
    }

    private Map<String, Long> seatIdsByLabel(long screenId) throws Exception {
        String layouts = mvc.perform(asAdmin(get("/api/v1/admin/screens/{id}/layouts", screenId)))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> seats = JsonPath.read(layouts, "$[0].seats");
        return seats.stream().collect(Collectors.toMap(
                seat -> (String) seat.get("label"), seat -> ((Number) seat.get("id")).longValue()));
    }
}
