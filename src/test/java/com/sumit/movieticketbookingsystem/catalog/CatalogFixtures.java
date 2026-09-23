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

    /** A fresh screen in a fresh theater and city. The theater charges ₹200 / ₹300 / ₹500 by default. */
    public long screen() throws Exception {
        long theaterId = theater(city(), uniqueName("Theater"));
        mvc.perform(asAdmin(put("/api/v1/admin/theaters/{id}/prices", theaterId)).content("""
                        {"prices": {"REGULAR": 20000, "PREMIUM": 30000, "RECLINER": 50000}}
                        """))
                .andExpect(status().isOk());
        return screen(theaterId, "Audi 1");
    }

    /** A fresh screen with an active 2 x 5 layout (A1-A5 regular, B1-B5 premium). */
    public long screenWithActiveLayout() throws Exception {
        long screenId = screen();
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
