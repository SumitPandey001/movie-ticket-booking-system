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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.idOf;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShowAdminIT {

    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    @Autowired
    private MockMvc mvc;

    private CatalogFixtures catalog;
    private long screenId;
    private long movieId;
    private OffsetDateTime sixPm;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new CatalogFixtures(mvc);
        screenId = catalog.screenWithActiveLayout();
        movieId = catalog.movie(150);                       // 2h30m, so a 18:00 show ends 20:30 and blocks until 20:50
        sixPm = OffsetDateTime.now(IST).plusDays(3).truncatedTo(ChronoUnit.DAYS).withHour(18);
    }

    @Test
    void createsAScheduledShowWithTimesFromTheMovie() throws Exception {
        createShow(sixPm)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.showDate").value(sixPm.toLocalDate().toString()))
                .andExpect(jsonPath("$.endTime").value(sixPm.plusMinutes(150).toInstant().toString()))
                .andExpect(jsonPath("$.language").value("HI"))
                .andExpect(jsonPath("$.totalSeats").value(10));
    }

    @Test
    void overlappingShowOnTheSameScreenIsRejected() throws Exception {
        createShow(sixPm).andExpect(status().isCreated());

        createShow(sixPm.plusHours(2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOW_OVERLAP"));
    }

    @Test
    void cleaningBufferKeepsTheScreenBlocked() throws Exception {
        createShow(sixPm).andExpect(status().isCreated());

        createShow(sixPm.plusMinutes(150 + 10)).andExpect(status().isConflict());   // inside the 20 min buffer
        createShow(sixPm.plusMinutes(150 + 20)).andExpect(status().isCreated());    // right when the buffer ends
    }

    @Test
    void cancelledShowFreesTheSlot() throws Exception {
        long showId = idOf(createShow(sixPm).andExpect(status().isCreated()).andReturn());

        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/cancel", showId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        createShow(sixPm).andExpect(status().isCreated());
    }

    @Test
    void showLifecycle() throws Exception {
        long showId = idOf(createShow(sixPm).andExpect(status().isCreated()).andReturn());

        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/open", showId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/open", showId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/cancel", showId)))
                .andExpect(status().isOk());
        mvc.perform(asAdmin(post("/api/v1/admin/shows/{id}/open", showId)))
                .andExpect(status().isConflict());
    }

    @Test
    void lateNightShowIsListedUnderThePreviousDay() throws Exception {
        OffsetDateTime halfPastMidnight = sixPm.plusDays(1).withHour(0).withMinute(30);

        createShow(halfPastMidnight)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.showDate").value(halfPastMidnight.toLocalDate().minusDays(1).toString()));
    }

    @Test
    void listsATheatersShowsForADate() throws Exception {
        long theaterId = JsonPath.<Number>read(
                createShow(sixPm).andReturn().getResponse().getContentAsString(), "$.theaterId").longValue();
        createShow(sixPm.plusHours(3));

        mvc.perform(asAdmin(get("/api/v1/admin/shows"))
                        .param("theaterId", String.valueOf(theaterId))
                        .param("date", sixPm.toLocalDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void rejectsShowsThatCantRun() throws Exception {
        createShow(OffsetDateTime.now(IST).minusHours(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Show must start in the future"));

        long screenWithoutLayout = catalog.screen();
        mvc.perform(asAdmin(post("/api/v1/admin/shows")).content(showJson(screenWithoutLayout, sixPm, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Screen " + screenWithoutLayout + " has no active seat layout"));

        mvc.perform(asAdmin(post("/api/v1/admin/shows"))
                        .content(showJson(screenId, sixPm, sixPm.toLocalDate().plusDays(2))))
                .andExpect(status().isBadRequest());
    }

    private ResultActions createShow(OffsetDateTime start) throws Exception {
        return mvc.perform(asAdmin(post("/api/v1/admin/shows")).content(showJson(screenId, start, null)));
    }

    private String showJson(long screen, OffsetDateTime start, LocalDate showDate) {
        return """
                {"movieId": %d, "screenId": %d, "startTime": "%s", "language": "hi", "format": "2D", "showDate": %s}
                """.formatted(movieId, screen, start, showDate == null ? "null" : "\"" + showDate + "\"");
    }
}
