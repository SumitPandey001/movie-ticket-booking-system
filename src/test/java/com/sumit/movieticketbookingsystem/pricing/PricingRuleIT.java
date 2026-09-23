package com.sumit.movieticketbookingsystem.pricing;

import com.sumit.movieticketbookingsystem.TestDates;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static com.sumit.movieticketbookingsystem.ApiRequests.idOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fixture shows charge ₹200 for REGULAR and ₹300 for PREMIUM. The seeded GLOBAL "Weekend +20%" rule applies on
 * Saturdays and Sundays. Rules created here are CITY or THEATER scoped to the test's own fresh data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PricingRuleIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private BookingFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new BookingFixtures(mvc, jdbc);
    }

    @Test
    void weekendShowsCostMoreEverywhereAPriceAppears() throws Exception {
        BookableShow saturday = fixtures.openShowOn(TestDates.saturday(2));

        assertPriceFrom(saturday, 24000);
        mvc.perform(asCustomer(get("/api/v1/shows/{id}/seats", saturday.id())))
                .andExpect(jsonPath("$.categories[*].pricePaise").value(contains(24000, 36000)));
        // 20000 + 4000 weekend = 24000; fee 2000; GST 18% of 24000 = 4320 and of the fee 360
        hold(saturday, "A1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats[0].amountPaise").value(24000 + 2000 + 4320 + 360))
                .andExpect(jsonPath("$.price.subtotalPaise").value(24000));
    }

    @Test
    void weekdayShowsHaveNoSurcharge() throws Exception {
        BookableShow weekday = fixtures.openShowOn(TestDates.weekday(2));

        assertPriceFrom(weekday, 20000);
        hold(weekday, "A1").andExpect(jsonPath("$.seats[0].amountPaise").value(25960));
    }

    @Test
    void theMostSpecificRuleWins() throws Exception {
        BookableShow saturday = fixtures.openShowOn(TestDates.saturday(2));
        long theaterId = jdbc.sql("SELECT theater_id FROM show WHERE id = ?")
                .param(saturday.id()).query(Long.class).single();
        long cityId = jdbc.sql("SELECT city_id FROM show WHERE id = ?").param(saturday.id()).query(Long.class).single();

        createRule("""
                {"name": "City Saturdays", "scopeType": "CITY", "scopeId": %d, "daysOfWeek": [6],
                 "adjustmentType": "PERCENT", "adjustmentValue": 10}
                """.formatted(cityId));
        seatMapRegularPrice(saturday, 22000);            // city beats global

        long theaterRule = createRule("""
                {"name": "Theater Saturdays", "scopeType": "THEATER", "scopeId": %d, "daysOfWeek": [6, 7],
                 "adjustmentType": "FLAT", "adjustmentValue": 1500}
                """.formatted(theaterId));
        seatMapRegularPrice(saturday, 21500);            // theater beats city

        mvc.perform(asAdmin(put("/api/v1/admin/pricing-rules/{id}", theaterRule)).content("""
                        {"name": "Theater Saturdays", "scopeType": "THEATER", "scopeId": %d, "daysOfWeek": [6, 7],
                         "adjustmentType": "FLAT", "adjustmentValue": 1500, "active": false}
                        """.formatted(theaterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        seatMapRegularPrice(saturday, 22000);            // paused, so the city rule is back

        mvc.perform(asAdmin(delete("/api/v1/admin/pricing-rules/{id}", theaterRule))).andExpect(status().isNoContent());
        mvc.perform(asAdmin(get("/api/v1/admin/pricing-rules")))
                .andExpect(jsonPath("$[*].name").value(hasItem("Weekend +20%")));
    }

    @Test
    void rulesOnlyApplyInsideTheirDates() throws Exception {
        BookableShow saturday = fixtures.openShowOn(TestDates.saturday(2));
        long cityId = jdbc.sql("SELECT city_id FROM show WHERE id = ?").param(saturday.id()).query(Long.class).single();
        createRule("""
                {"name": "Expired promo", "scopeType": "CITY", "scopeId": %d, "daysOfWeek": [6],
                 "adjustmentType": "FLAT", "adjustmentValue": 9900, "validFrom": "2020-01-01", "validTo": "2020-12-31"}
                """.formatted(cityId));

        seatMapRegularPrice(saturday, 24000);            // still the global weekend rule
    }

    @Test
    void rejectsRulesThatDontMakeSense() throws Exception {
        rule("""
                {"name": "x", "scopeType": "GLOBAL", "scopeId": 1, "daysOfWeek": [6],
                 "adjustmentType": "PERCENT", "adjustmentValue": 10}
                """).andExpect(status().isBadRequest());
        rule("""
                {"name": "x", "scopeType": "THEATER", "scopeId": %d, "daysOfWeek": [6],
                 "adjustmentType": "PERCENT", "adjustmentValue": 10}
                """.formatted(Long.MAX_VALUE)).andExpect(status().isNotFound());
        rule("""
                {"name": "x", "scopeType": "GLOBAL", "daysOfWeek": [8],
                 "adjustmentType": "PERCENT", "adjustmentValue": 10}
                """).andExpect(status().isBadRequest());
        rule("""
                {"name": "x", "scopeType": "GLOBAL", "daysOfWeek": [6],
                 "adjustmentType": "PERCENT", "adjustmentValue": 150}
                """).andExpect(status().isBadRequest());
        rule("""
                {"name": "x", "scopeType": "GLOBAL", "daysOfWeek": [6], "adjustmentType": "FLAT",
                 "adjustmentValue": 100, "validFrom": "2026-12-31", "validTo": "2026-01-01"}
                """).andExpect(status().isBadRequest());
    }

    private void assertPriceFrom(BookableShow show, long expected) throws Exception {
        mvc.perform(asAdmin(get("/api/v1/admin/shows"))
                        .param("theaterId", String.valueOf(theaterOf(show)))
                        .param("date", jdbc.sql("SELECT show_date FROM show WHERE id = ?").param(show.id())
                                .query(String.class).single()))
                .andExpect(jsonPath("$[0].priceFromPaise").value(expected));
    }

    private void seatMapRegularPrice(BookableShow show, long expected) throws Exception {
        mvc.perform(asCustomer(get("/api/v1/shows/{id}/seats", show.id())))
                .andExpect(jsonPath("$.categories[0].code").value("REGULAR"))
                .andExpect(jsonPath("$.categories[0].pricePaise").value(expected));
    }

    private long theaterOf(BookableShow show) {
        return jdbc.sql("SELECT theater_id FROM show WHERE id = ?").param(show.id()).query(Long.class).single();
    }

    private ResultActions hold(BookableShow show, String label) throws Exception {
        return mvc.perform(asCustomer(post("/api/v1/bookings"))
                .header("Idempotency-Key", UUID.randomUUID())
                .content("""
                        {"showId": %d, "seatIds": %s}
                        """.formatted(show.id(), show.seats(label))));
    }

    private long createRule(String json) throws Exception {
        return idOf(rule(json).andExpect(status().isCreated()).andReturn());
    }

    private ResultActions rule(String json) throws Exception {
        return mvc.perform(asAdmin(post("/api/v1/admin/pricing-rules")).content(json));
    }
}
