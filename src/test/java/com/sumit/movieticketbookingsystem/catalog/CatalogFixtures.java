package com.sumit.movieticketbookingsystem.catalog;

import org.springframework.test.web.servlet.MockMvc;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.idOf;
import static com.sumit.movieticketbookingsystem.ApiRequests.uniqueName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    /** A fresh screen in a fresh theater and city. */
    public long screen() throws Exception {
        return screen(theater(city(), uniqueName("Theater")), "Audi 1");
    }

    public long create(String path, String json) throws Exception {
        return idOf(mvc.perform(asAdmin(post(path)).content(json))
                .andExpect(status().isCreated())
                .andReturn());
    }
}
