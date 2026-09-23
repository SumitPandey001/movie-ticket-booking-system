package com.sumit.movieticketbookingsystem.catalog;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static com.sumit.movieticketbookingsystem.ApiRequests.idOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MovieApiIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void createUpdateAndReadMovie() throws Exception {
        long id = idOf(mvc.perform(asAdmin(post("/api/v1/admin/movies")).content("""
                        {"title": " Kalki 2898 AD ", "durationMin": 181, "certification": "UA",
                         "releaseDate": "2024-06-27"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Kalki 2898 AD"))
                .andReturn());

        mvc.perform(asAdmin(put("/api/v1/admin/movies/{id}", id)).content("""
                        {"title": "Kalki 2898 AD", "durationMin": 176, "certification": "UA"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMin").value(176))
                .andExpect(jsonPath("$.releaseDate").doesNotExist());

        mvc.perform(asCustomer(get("/api/v1/movies/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certification").value("UA"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void deactivatedMovieCanStillBeRead() throws Exception {
        long id = idOf(mvc.perform(asAdmin(post("/api/v1/admin/movies")).content("""
                        {"title": "Sholay", "durationMin": 204, "certification": "U"}
                        """))
                .andExpect(status().isCreated())
                .andReturn());

        mvc.perform(asAdmin(post("/api/v1/admin/movies/{id}/deactivate", id)))
                .andExpect(status().isNoContent());
        mvc.perform(asCustomer(get("/api/v1/movies/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void rejectsInvalidMovies() throws Exception {
        mvc.perform(asAdmin(post("/api/v1/admin/movies")).content("""
                        {"title": "", "durationMin": 0}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("title", "durationMin", "certification")));

        // an unknown enum value fails JSON parsing, before bean validation
        mvc.perform(asAdmin(post("/api/v1/admin/movies")).content("""
                        {"title": "X", "durationMin": 90, "certification": "PG-13"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownMovieIsNotFound() throws Exception {
        mvc.perform(asCustomer(get("/api/v1/movies/{id}", Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
    }
}
