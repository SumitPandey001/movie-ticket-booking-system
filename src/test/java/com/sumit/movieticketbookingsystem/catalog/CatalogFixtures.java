package com.sumit.movieticketbookingsystem.catalog;

import org.springframework.test.web.servlet.MockMvc;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.idOf;
import static com.sumit.movieticketbookingsystem.ApiRequests.uniqueName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creates catalog data through the admin API, the same way an admin would.
 */
public class CatalogFixtures {

    private final MockMvc mvc;

    public CatalogFixtures(MockMvc mvc) {
        this.mvc = mvc;
    }

    public long city() throws Exception {
        return create("/api/v1/admin/cities", """
                {"name": "%s"}
                """.formatted(uniqueName("City")));
    }

    public long theater(long cityId, String name) throws Exception {
        return create("/api/v1/admin/theaters", """
                {"cityId": %d, "name": "%s"}
                """.formatted(cityId, name));
    }

    public long screen(long theaterId, String name) throws Exception {
        return create("/api/v1/admin/theaters/" + theaterId + "/screens", """
                {"name": "%s"}
                """.formatted(name));
    }

    /** A theater that charges ₹200 / ₹300 / ₹500 by default for REGULAR / PREMIUM / RECLINER. */
    public long pricedTheater(long cityId, String name) throws Exception {
        long theaterId = theater(cityId, name);
        mvc.perform(asAdmin(put("/api/v1/admin/theaters/{id}/prices", theaterId)).content("""
                        {"prices": {"REGULAR": 20000, "PREMIUM": 30000, "RECLINER": 50000}}
                        """))
                .andExpect(status().isOk());
        return theaterId;
    }

    /** A fresh screen in a fresh priced theater and city. */
    public long screen() throws Exception {
        return screen(pricedTheater(city(), uniqueName("Theater")), "Audi 1");
    }

    /** A fresh screen with an active 2 x 5 layout (A1-A5 regular, B1-B5 premium). */
    public long screenWithActiveLayout() throws Exception {
        return activateSmallLayout(screen());
    }

    /** Another screen with the same 2 x 5 layout in an existing (priced) theater. */
    public long screenWithActiveLayout(long theaterId) throws Exception {
        return activateSmallLayout(screen(theaterId, uniqueName("Audi")));
    }

    private long activateSmallLayout(long screenId) throws Exception {
        long layoutId = create("/api/v1/admin/screens/" + screenId + "/layouts", """
                {"rows": [{"label": "A", "segments": [{"from": 1, "to": 5, "category": "REGULAR"}]},
                          {"label": "B", "segments": [{"from": 1, "to": 5, "category": "PREMIUM"}]}]}
                """);
        mvc.perform(asAdmin(post("/api/v1/admin/layouts/{id}/activate", layoutId)))
                .andExpect(status().isOk());
        return screenId;
    }

    public long movie(int durationMin) throws Exception {
        return create("/api/v1/admin/movies", """
                {"title": "%s", "durationMin": %d, "certification": "UA"}
                """.formatted(uniqueName("Movie"), durationMin));
    }

    public long create(String path, String json) throws Exception {
        return idOf(mvc.perform(asAdmin(post(path)).content(json))
                .andExpect(status().isCreated())
                .andReturn());
    }
}
