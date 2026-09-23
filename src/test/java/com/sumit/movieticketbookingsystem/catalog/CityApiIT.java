package com.sumit.movieticketbookingsystem.catalog;

import com.jayway.jsonpath.JsonPath;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

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

    private static final UUID ADMIN_ID = UUID.fromString("0f8d7c1a-0000-4000-8000-00000000a001");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void adminCreatesCityWithDefaultTimezoneAndAuditColumns() throws Exception {
        String name = uniqueName("Pune");

        String body = mvc.perform(asAdmin(post("/api/v1/admin/cities"))
                        .content("""
                                {"name": "  %s  ", "state": "Maharashtra"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        long id = idOf(body);
        UUID createdBy = jdbc.sql("SELECT created_by FROM city WHERE id = ?").param(id).query(UUID.class).single();
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
        String body = mvc.perform(asAdmin(post("/api/v1/admin/cities")).content(cityJson(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    private static long idOf(String responseBody) {
        return JsonPath.<Number>read(responseBody, "$.id").longValue();
    }

    private static String cityJson(String name) {
        return """
                {"name": "%s"}
                """.formatted(name);
    }

    // tests share one database, so every city gets a name no other test uses
    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ADMIN");
    }

    private static MockHttpServletRequestBuilder asCustomer(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "CUSTOMER");
    }
}
