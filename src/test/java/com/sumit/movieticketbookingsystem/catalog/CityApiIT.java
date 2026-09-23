package com.sumit.movieticketbookingsystem.catalog;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.ADMIN_ID;
import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static com.sumit.movieticketbookingsystem.ApiRequests.idOf;
import static com.sumit.movieticketbookingsystem.ApiRequests.uniqueName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CityApiIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void adminCreatesCityWithDefaultTimezoneAndAuditColumns() throws Exception {
        String name = uniqueName("Pune");

        var result = mvc.perform(asAdmin(post("/api/v1/admin/cities"))
                        .content("""
                                {"name": "  %s  ", "state": "Maharashtra"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        UUID createdBy = jdbc.sql("SELECT created_by FROM city WHERE id = ?")
                .param(idOf(result)).query(UUID.class).single();
        assertThat(createdBy).isEqualTo(ADMIN_ID);
    }

    @Test
    void cityNamesAreUniqueIgnoringCase() throws Exception {
        String name = uniqueName("Delhi");
        createCity(name);

        mvc.perform(asAdmin(post("/api/v1/admin/cities")).content(cityJson(name.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_EXISTS"));
    }

    @Test
    void rejectsUnknownTimezoneAndBlankName() throws Exception {
        mvc.perform(asAdmin(post("/api/v1/admin/cities"))
                        .content("""
                                {"name": "%s", "timezone": "Mars/Olympus"}
                                """.formatted(uniqueName("Goa"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown timezone: Mars/Olympus"));

        mvc.perform(asAdmin(post("/api/v1/admin/cities")).content(cityJson(" ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void updatedAndDeactivatedCities() throws Exception {
        long id = createCity(uniqueName("Bombay"));
        String renamed = uniqueName("Mumbai");

        mvc.perform(asAdmin(put("/api/v1/admin/cities/{id}", id)).content(cityJson(renamed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamed));
        mvc.perform(asCustomer(get("/api/v1/cities")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(hasItem(renamed)));

        mvc.perform(asAdmin(post("/api/v1/admin/cities/{id}/deactivate", id)))
                .andExpect(status().isNoContent());
        mvc.perform(asCustomer(get("/api/v1/cities")))
                .andExpect(jsonPath("$[*].name").value(not(hasItem(renamed))));
    }

    @Test
    void unknownCityIsNotFound() throws Exception {
        mvc.perform(asAdmin(put("/api/v1/admin/cities/{id}", Long.MAX_VALUE)).content(cityJson(uniqueName("X"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void customersCannotManageCities() throws Exception {
        mvc.perform(asCustomer(post("/api/v1/admin/cities")).content(cityJson(uniqueName("Agra"))))
                .andExpect(status().isForbidden());
    }

    private long createCity(String name) throws Exception {
        return idOf(mvc.perform(asAdmin(post("/api/v1/admin/cities")).content(cityJson(name)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private static String cityJson(String name) {
        return """
                {"name": "%s"}
                """.formatted(name);
    }
}
