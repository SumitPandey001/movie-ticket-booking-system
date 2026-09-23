package com.sumit.movieticketbookingsystem.catalog;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LayoutActivationIT {

    private static final String SMALL_LAYOUT = """
            {"rows": [
               {"label": "A", "segments": [{"from": 1, "to": 4, "category": "REGULAR"}, {"aisle": 1},
                                           {"from": 5, "to": 8, "category": "REGULAR"}]},
               {"label": "B", "segments": [{"from": 1, "to": 4, "category": "RECLINER"}]}],
             "blocked": ["A5"], "wheelchair": ["B1"]}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private CatalogFixtures fixtures;
    private long screenId;

    @BeforeEach
    void setUp() throws Exception {
        fixtures = new CatalogFixtures(mvc);
        screenId = fixtures.screen();
    }

    @Test
    void draftHasTheGridFromTheRequest() throws Exception {
        mvc.perform(asAdmin(post("/api/v1/admin/screens/{id}/layouts", screenId)).content(SMALL_LAYOUT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.gridRows").value(2))
                .andExpect(jsonPath("$.gridCols").value(9))
                .andExpect(jsonPath("$.totalSeats").value(11))       // 12 seats, A5 blocked
                .andExpect(jsonPath("$.seats", hasSize(12)))
                .andExpect(jsonPath("$.seats[4].label").value("A5"))
                .andExpect(jsonPath("$.seats[4].col").value(6))
                .andExpect(jsonPath("$.seats[4].type").value("BLOCKED"))
                .andExpect(jsonPath("$.seats[8].label").value("B1"))
                .andExpect(jsonPath("$.seats[8].category").value("RECLINER"))
                .andExpect(jsonPath("$.seats[8].type").value("WHEELCHAIR"));
    }

    @Test
    void activatingANewVersionRetiresTheOldOne() throws Exception {
        long v1 = draft(SMALL_LAYOUT);
        activate(v1);
        long v2 = draft(SMALL_LAYOUT);
        activate(v2);

        mvc.perform(asAdmin(get("/api/v1/admin/screens/{id}/layouts", screenId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].version").value(contains(1, 2)))
                .andExpect(jsonPath("$[*].status").value(contains("RETIRED", "ACTIVE")));
        assertThat(activeLayouts()).isEqualTo(1);
    }

    @Test
    void onlyDraftsCanBeEditedOrActivated() throws Exception {
        long layoutId = draft(SMALL_LAYOUT);
        activate(layoutId);

        mvc.perform(asAdmin(put("/api/v1/admin/layouts/{id}", layoutId)).content(SMALL_LAYOUT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mvc.perform(asAdmin(post("/api/v1/admin/layouts/{id}/activate", layoutId)))
                .andExpect(status().isConflict());
        assertThat(activeLayouts()).isEqualTo(1);
    }

    @Test
    void editingADraftReplacesItsSeats() throws Exception {
        long layoutId = draft(SMALL_LAYOUT);

        // same labels as before, so this only works if the old seats are deleted first
        mvc.perform(asAdmin(put("/api/v1/admin/layouts/{id}", layoutId)).content("""
                        {"rows": [{"label": "A", "segments": [{"from": 1, "to": 6, "category": "PREMIUM"}]}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats", hasSize(6)))
                .andExpect(jsonPath("$.seats[0].category").value("PREMIUM"))
                .andExpect(jsonPath("$.gridRows").value(1));
    }

    @Test
    void rejectsBadLayouts() throws Exception {
        mvc.perform(asAdmin(post("/api/v1/admin/screens/{id}/layouts", screenId)).content("""
                        {"rows": [{"label": "A", "segments": [{"from": 1, "to": 4, "category": "GOLD"}]}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown seat category: GOLD"));

        mvc.perform(asAdmin(post("/api/v1/admin/screens/{id}/layouts", screenId)).content("""
                        {"rows": [{"label": "A", "segments": [{"from": 1, "to": 4, "aisle": 2}]}]}
                        """))
                .andExpect(status().isBadRequest());

        mvc.perform(asAdmin(post("/api/v1/admin/screens/{id}/layouts", screenId)).content("""
                        {"rows": [{"label": "A", "segments": [{"from": 1, "to": 4, "category": "REGULAR"}]}],
                         "blocked": ["not a seat"]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(asAdmin(post("/api/v1/admin/screens/{id}/layouts", Long.MAX_VALUE)).content(SMALL_LAYOUT))
                .andExpect(status().isNotFound());
    }

    private long draft(String layoutJson) throws Exception {
        return fixtures.create("/api/v1/admin/screens/" + screenId + "/layouts", layoutJson);
    }

    private void activate(long layoutId) throws Exception {
        mvc.perform(asAdmin(post("/api/v1/admin/layouts/{id}/activate", layoutId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private long activeLayouts() {
        return jdbc.sql("SELECT count(*) FROM seat_layout WHERE screen_id = ? AND status = 'ACTIVE'")
                .param(screenId).query(Long.class).single();
    }
}
