package com.sumit.movieticketbookingsystem.booking;

import com.jayway.jsonpath.JsonPath;
import com.sumit.movieticketbookingsystem.TestDates;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import com.sumit.movieticketbookingsystem.catalog.CatalogFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.ZoneOffset;
import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static com.sumit.movieticketbookingsystem.ApiRequests.idOf;
import static com.sumit.movieticketbookingsystem.ApiRequests.uniqueName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefundPolicyIT {

    private static final String PATH = "/api/v1/admin/refund-policies";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void theStandardPolicyIsTheDefaultOutOfTheBox() throws Exception {
        long standard = standardPolicyId();

        mvc.perform(asAdmin(get(PATH + "/{id}", standard)))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.type").value("SLAB"))
                .andExpect(jsonPath("$.slabs[*].minHoursBefore").value(contains(24, 4, 0)))
                .andExpect(jsonPath("$.slabs[*].percent").value(contains(100, 50, 0)));
    }

    @Test
    void slabsAreCheckedAndStoredLatestFirst() throws Exception {
        String name = uniqueName("Flexi");
        create(name, "SLAB", true, "[{\"minHoursBefore\": 2, \"percent\": 75}, "
                + "{\"minHoursBefore\": 48, \"percent\": 100}]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundFees").value(true))
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.slabs[0].minHoursBefore").value(48));

        create(name.toUpperCase(), "FULL", false, "[]")
                .andExpect(status().isConflict());
        create(uniqueName("Odd"), "SLAB", false, "[{\"minHoursBefore\": 24, \"percent\": 50}, "
                + "{\"minHoursBefore\": 4, \"percent\": 80}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cancelling later can't refund more than cancelling earlier"));
        create(uniqueName("Odd"), "SLAB", false, "[]")
                .andExpect(status().isBadRequest());
        create(uniqueName("Odd"), "FULL", false, "[{\"minHoursBefore\": 4, \"percent\": 80}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only SLAB policies have slabs and refundFees"));
        create(uniqueName("Odd"), "SLAB", false, "[{\"minHoursBefore\": 4, \"percent\": 101}]")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aConfirmedBookingKeepsThePolicyItWasSoldUnder() throws Exception {
        long policy = idOf(create(uniqueName("Strict"), "SLAB", false,
                "[{\"minHoursBefore\": 72, \"percent\": 100}]").andReturn());
        BookableShow show = new BookingFixtures(mvc, jdbc).openShow();
        jdbc.sql("UPDATE show SET refund_policy_id = ? WHERE id = ?").params(policy, show.id()).update();

        String bookingId = confirmedBooking(show);
        mvc.perform(asAdmin(put(PATH + "/{id}", policy))
                        .content("{\"name\": \"%s\", \"type\": \"NON_REFUNDABLE\"}".formatted(uniqueName("Strict"))))
                .andExpect(status().isOk());

        String snapshot = snapshotOf(bookingId);
        assertThat(JsonPath.<Integer>read(snapshot, "$.policyId")).isEqualTo((int) policy);
        assertThat(JsonPath.<String>read(snapshot, "$.type")).isEqualTo("SLAB");
        assertThat(JsonPath.<Integer>read(snapshot, "$.slabs[0].minHoursBefore")).isEqualTo(72);
    }

    @Test
    void aShowWithoutItsOwnPolicyUsesTheDefault() throws Exception {
        long standard = standardPolicyId();
        long full = idOf(create(uniqueName("Generous"), "FULL", false, "[]").andReturn());
        BookableShow show = new BookingFixtures(mvc, jdbc).openShow();
        try {
            mvc.perform(asAdmin(post(PATH + "/{id}/make-default", full)))
                    .andExpect(jsonPath("$.isDefault").value(true));
            mvc.perform(asAdmin(get(PATH + "/{id}", standard))).andExpect(jsonPath("$.isDefault").value(false));

            assertThat(JsonPath.<String>read(snapshotOf(confirmedBooking(show)), "$.type")).isEqualTo("FULL");
        } finally {
            mvc.perform(asAdmin(post(PATH + "/{id}/make-default", standard))).andExpect(status().isOk());
        }
    }

    @Test
    void aShowCanOnlyPointAtAPolicyThatExists() throws Exception {
        CatalogFixtures catalog = new CatalogFixtures(mvc);
        String start = TestDates.weekday(3).atTime(10, 0).atOffset(ZoneOffset.ofHoursMinutes(5, 30)).toString();
        String show = """
                {"movieId": %d, "screenId": %d, "startTime": "%s", "language": "EN", "format": "2D",
                 "refundPolicyId": %d}""";

        mvc.perform(asAdmin(post("/api/v1/admin/shows"))
                        .content(show.formatted(catalog.movie(120), catalog.screenWithActiveLayout(), start, 999999)))
                .andExpect(status().isNotFound());
        mvc.perform(asAdmin(post("/api/v1/admin/shows"))
                        .content(show.formatted(catalog.movie(120), catalog.screenWithActiveLayout(), start,
                                standardPolicyId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundPolicyId").value(standardPolicyId()));
    }

    private ResultActions create(String name, String type, boolean refundFees, String slabs) throws Exception {
        return mvc.perform(asAdmin(post(PATH)).content("""
                {"name": "%s", "type": "%s", "refundFees": %s, "slabs": %s}
                """.formatted(name, type, refundFees, slabs)));
    }

    private String confirmedBooking(BookableShow show) throws Exception {
        UUID customer = UUID.randomUUID();
        String hold = mvc.perform(asCustomer(post("/api/v1/bookings"), customer)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("{\"showId\": %d, \"seatIds\": %s}".formatted(show.id(), show.seats("A1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String bookingId = JsonPath.read(hold, "$.bookingId");
        mvc.perform(asCustomer(post("/api/v1/bookings/{id}/payments", bookingId), customer)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("""
                                {"details": {"type": "UPI", "vpa": "asha@okbank"}, "simulate": "SUCCESS"}"""))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
        return bookingId;
    }

    private String snapshotOf(String bookingId) {
        return jdbc.sql("SELECT refund_policy_snapshot::text FROM booking WHERE id = ?")
                .param(UUID.fromString(bookingId)).query(String.class).single();
    }

    private long standardPolicyId() {
        return jdbc.sql("SELECT id FROM refund_policy WHERE name = 'Standard'").query(Long.class).single();
    }
}
