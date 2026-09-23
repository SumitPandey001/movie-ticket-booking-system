package com.sumit.movieticketbookingsystem.catalog;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TheaterApiIT {

    @Autowired
    private MockMvc mvc;

    private CatalogFixtures fixtures;
    private long cityId;

    @BeforeEach
    void createCity() throws Exception {
        fixtures = new CatalogFixtures(mvc);
        cityId = fixtures.city();
    }

    @Test
    void theaterWithScreens() throws Exception {
        long theaterId = fixtures.theater(cityId, "PVR Grand");
        long audi1 = fixtures.screen(theaterId, "Audi 1");
        fixtures.screen(theaterId, "Audi 2");

        mvc.perform(asAdmin(put("/api/v1/admin/screens/{id}", audi1)).content(screenJson("IMAX")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("IMAX"));

        mvc.perform(asAdmin(get("/api/v1/admin/theaters/{id}", theaterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cityId").value(cityId))
                .andExpect(jsonPath("$.screens[*].name").value(contains("IMAX", "Audi 2")));
    }

    @Test
    void theaterNamesAreUniquePerCity() throws Exception {
        fixtures.theater(cityId, "Inox");

        mvc.perform(asAdmin(post("/api/v1/admin/theaters")).content(theaterJson(cityId, "INOX")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_EXISTS"));

        fixtures.theater(fixtures.city(), "Inox");
    }

    @Test
    void screenNamesAreUniquePerTheater() throws Exception {
        long theaterId = fixtures.theater(cityId, "Sathyam");
        long audi1 = fixtures.screen(theaterId, "Audi 1");
        fixtures.screen(theaterId, "Audi 2");

        mvc.perform(asAdmin(post("/api/v1/admin/theaters/{id}/screens", theaterId)).content(screenJson("audi 1")))
                .andExpect(status().isConflict());
        mvc.perform(asAdmin(put("/api/v1/admin/screens/{id}", audi1)).content(screenJson("Audi 2")))
                .andExpect(status().isConflict());
    }

    @Test
    void cityMustExistAndBeActive() throws Exception {
        mvc.perform(asAdmin(post("/api/v1/admin/theaters")).content(theaterJson(Long.MAX_VALUE, "Nowhere")))
                .andExpect(status().isNotFound());

        mvc.perform(asAdmin(post("/api/v1/admin/cities/{id}/deactivate", cityId)))
                .andExpect(status().isNoContent());
        mvc.perform(asAdmin(post("/api/v1/admin/theaters")).content(theaterJson(cityId, "Closed")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noNewScreensOnAnInactiveTheater() throws Exception {
        long theaterId = fixtures.theater(cityId, "Old Talkies");
        long screenId = fixtures.screen(theaterId, "Main");

        mvc.perform(asAdmin(post("/api/v1/admin/screens/{id}/deactivate", screenId)))
                .andExpect(status().isNoContent());
        mvc.perform(asAdmin(post("/api/v1/admin/theaters/{id}/deactivate", theaterId)))
                .andExpect(status().isNoContent());

        mvc.perform(asAdmin(post("/api/v1/admin/theaters/{id}/screens", theaterId)).content(screenJson("Annex")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(asAdmin(get("/api/v1/admin/theaters/{id}", theaterId)))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.screens[0].active").value(false));
    }

    @Test
    void unknownScreenIsNotFound() throws Exception {
        mvc.perform(asAdmin(put("/api/v1/admin/screens/{id}", Long.MAX_VALUE)).content(screenJson("X")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Screen " + Long.MAX_VALUE + " not found"));
    }

    private static String theaterJson(long cityId, String name) {
        return """
                {"cityId": %d, "name": "%s", "area": "Anna Nagar"}
                """.formatted(cityId, name);
    }

    private static String screenJson(String name) {
        return """
                {"name": "%s"}
                """.formatted(name);
    }
}
