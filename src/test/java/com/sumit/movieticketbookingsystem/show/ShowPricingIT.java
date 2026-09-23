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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static com.sumit.movieticketbookingsystem.ApiRequests.idOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShowPricingIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private CatalogFixtures catalog;
    private long screenId;          // 2 x 5 layout: REGULAR and PREMIUM seats
    private long theaterId;
    private long movieId;
    private OffsetDateTime start;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new CatalogFixtures(mvc);
        screenId = catalog.screenWithActiveLayout();
        theaterId = jdbc.sql("SELECT theater_id FROM screen WHERE id = ?").param(screenId).query(Long.class).single();
        movieId = catalog.movie(120);
        start = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).plusDays(4).truncatedTo(ChronoUnit.DAYS).withHour(15);
    }

    @Test
    void showCopiesTheTheaterDefaults() throws Exception {
        long showId = idOf(createShow(null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceFromPaise").value(20000))
                .andReturn());

        assertThat(showPrices(showId)).containsExactlyInAnyOrderEntriesOf(Map.of("REGULAR", 20000L, "PREMIUM", 30000L));
    }

    @Test
    void laterTheaterPriceChangesDontTouchExistingShows() throws Exception {
        long showId = idOf(createShow(null).andExpect(status().isCreated()).andReturn());

        mvc.perform(asAdmin(put("/api/v1/admin/theaters/{id}/prices", theaterId)).content("""
                        {"prices": {"REGULAR": 25000}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prices.REGULAR").value(25000))
                .andExpect(jsonPath("$.prices.PREMIUM").value(30000));

        assertThat(showPrices(showId)).containsEntry("REGULAR", 20000L);
    }

    @Test
    void adminCanOverridePricesForOneShow() throws Exception {
        long showId = idOf(createShow("{\"REGULAR\": 15000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceFromPaise").value(15000))
                .andReturn());

        mvc.perform(asAdmin(put("/api/v1/admin/shows/{id}/prices", showId)).content("""
                        {"prices": {"REGULAR": 35000}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceFromPaise").value(30000));   // PREMIUM is now the cheapest

        assertThat(showPrices(showId)).containsEntry("REGULAR", 35000L);
        assertThat(jdbc.sql("SELECT overridden FROM show_category_price WHERE show_id = ? AND price_paise = 35000")
                .param(showId).query(Boolean.class).single()).isTrue();
    }

    @Test
    void overridesMustMatchTheShowsCategories() throws Exception {
        createShow("{\"RECLINER\": 60000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The show has no RECLINER seats"));
        createShow("{\"GOLD\": 60000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown seat category: GOLD"));
        createShow("{\"REGULAR\": 0}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void theaterNeedsAPriceForEveryCategoryTheShowSells() throws Exception {
        long bareTheater = catalog.theater(catalog.city(), "No Prices Yet");
        long bareScreen = catalog.screen(bareTheater, "Audi 1");
        long layoutId = catalog.create("/api/v1/admin/screens/" + bareScreen + "/layouts", """
                {"rows": [{"label": "A", "segments": [{"from": 1, "to": 4, "category": "REGULAR"}]}]}
                """);
        mvc.perform(asAdmin(post("/api/v1/admin/layouts/{id}/activate", layoutId))).andExpect(status().isOk());

        mvc.perform(asAdmin(post("/api/v1/admin/shows")).content(showJson(bareScreen, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Theater " + bareTheater + " has no price for REGULAR seats"));
        // and nothing was left behind by the failed attempt
        assertThat(jdbc.sql("SELECT count(*) FROM show WHERE screen_id = ?").param(bareScreen).query(Long.class).single())
                .isZero();
    }

    @Test
    void theaterPricesNeedAnExistingTheaterAndAnAdmin() throws Exception {
        mvc.perform(asAdmin(put("/api/v1/admin/theaters/{id}/prices", Long.MAX_VALUE)).content("""
                        {"prices": {"REGULAR": 25000}}
                        """))
                .andExpect(status().isNotFound());
        mvc.perform(asCustomer(get("/api/v1/admin/theaters/{id}/prices", theaterId)))
                .andExpect(status().isForbidden());
    }

    private ResultActions createShow(String overridesJson) throws Exception {
        return mvc.perform(asAdmin(post("/api/v1/admin/shows")).content(showJson(screenId, overridesJson)));
    }

    private String showJson(long screen, String overridesJson) {
        return """
                {"movieId": %d, "screenId": %d, "startTime": "%s", "language": "TA", "format": "IMAX",
                 "priceOverrides": %s}
                """.formatted(movieId, screen, start, overridesJson);
    }

    private Map<String, Long> showPrices(long showId) {
        return jdbc.sql("""
                        SELECT c.code, p.price_paise FROM show_category_price p
                        JOIN seat_category c ON c.id = p.category_id WHERE p.show_id = ?
                        """)
                .param(showId)
                .query((rs, row) -> Map.entry(rs.getString(1), rs.getLong(2)))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
