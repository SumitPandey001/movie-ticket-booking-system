package com.sumit.movieticketbookingsystem.booking;

import com.jayway.jsonpath.JsonPath;
import com.sumit.movieticketbookingsystem.TestDates;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import com.sumit.movieticketbookingsystem.pricing.CouponFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Coupons on real holds. Fixture prices: REGULAR ₹200, PREMIUM ₹300; Saturdays +20%.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BookingCouponIT {

    private static final String FIFTY_OFF = """
            "discountType": "FLAT", "discountValue": 5000, "minOrderPaise": 30000, "perUserLimit": 1""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private BookingFixtures shows;
    private CouponFixtures coupons;
    private UUID customer;

    @BeforeEach
    void setUp() {
        shows = new BookingFixtures(mvc, jdbc);
        coupons = new CouponFixtures(mvc);
        customer = UUID.randomUUID();
    }

    @Test
    void holdWithACouponMatchesTheWorkedExample() throws Exception {
        BookableShow saturday = shows.openShowOn(TestDates.saturday(2));
        String code = coupons.coupon(FIFTY_OFF);

        hold(saturday, code, "B1", "B2")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coupon").value(code))
                .andExpect(jsonPath("$.seats[0].amountPaise").value(41890))
                .andExpect(jsonPath("$.seats[1].amountPaise").value(41890))
                .andExpect(jsonPath("$.price.subtotalPaise").value(72000))
                .andExpect(jsonPath("$.price.discountPaise").value(5000))
                .andExpect(jsonPath("$.price.totalPaise").value(83780));
        assertThat(usedCount(code)).isEqualTo(1);
    }

    @Test
    void couponCanBeAddedSwappedAndRemovedOnAHold() throws Exception {
        BookableShow weekday = shows.openShowOn(TestDates.weekday(2));
        String fiftyOff = coupons.coupon(FIFTY_OFF);
        String tenPercent = coupons.coupon("""
                "discountType": "PERCENT", "discountValue": 10""");
        String bookingId = bookingId(hold(weekday, null, "A1", "A2")
                .andExpect(jsonPath("$.price.totalPaise").value(51920)));       // 2 x 25960

        changeCoupon(bookingId, fiftyOff)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.discountPaise").value(5000))
                .andExpect(jsonPath("$.seats[0].amountPaise").value(25960 - 2500 - 450));   // less discount and its GST
        changeCoupon(bookingId, tenPercent)
                .andExpect(jsonPath("$.coupon").value(tenPercent))
                .andExpect(jsonPath("$.price.discountPaise").value(4000));
        assertThat(usedCount(fiftyOff)).isZero();                                          // given back on the swap
        assertThat(usedCount(tenPercent)).isEqualTo(1);

        mvc.perform(asCustomer(delete("/api/v1/bookings/{id}/coupon", bookingId), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupon").doesNotExist())
                .andExpect(jsonPath("$.price.totalPaise").value(51920));
        assertThat(usedCount(tenPercent)).isZero();
    }

    @Test
    void releasingAHoldGivesTheCouponBack() throws Exception {
        BookableShow weekday = shows.openShowOn(TestDates.weekday(2));
        String code = coupons.coupon(FIFTY_OFF);
        String bookingId = bookingId(hold(weekday, code, "B3").andExpect(status().isCreated()));

        mvc.perform(asCustomer(post("/api/v1/bookings/{id}/release", bookingId), customer)).andExpect(status().isOk());

        assertThat(usedCount(code)).isZero();
        hold(weekday, code, "B4").andExpect(status().isCreated());       // so the same customer may use it again
    }

    @Test
    void perUserLimitHoldsAcrossShows() throws Exception {
        String code = coupons.coupon(FIFTY_OFF);
        hold(shows.openShow(), code, "B1").andExpect(status().isCreated());

        hold(shows.openShow(), code, "B1")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID"))
                .andExpect(jsonPath("$.detail").value("You've already used this coupon"));
        assertThat(usedCount(code)).isEqualTo(1);
    }

    @Test
    void anInvalidCouponFailsTheWholeHold() throws Exception {
        BookableShow weekday = shows.openShowOn(TestDates.weekday(2));
        String bigOrdersOnly = coupons.coupon("""
                "discountType": "FLAT", "discountValue": 5000, "minOrderPaise": 100000""");

        hold(weekday, bigOrdersOnly, "A3")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID"));
        assertThat(jdbc.sql("SELECT status FROM show_seat WHERE show_id = ? AND layout_seat_id = ?")
                .params(weekday.id(), weekday.seatIdsByLabel().get("A3")).query(String.class).single())
                .isEqualTo("AVAILABLE");                                     // the seat claim was rolled back too
    }

    private ResultActions hold(BookableShow show, String couponCode, String... labels) throws Exception {
        return mvc.perform(asCustomer(post("/api/v1/bookings"), customer)
                .header("Idempotency-Key", UUID.randomUUID())
                .content("""
                        {"showId": %d, "seatIds": %s, "couponCode": %s}
                        """.formatted(show.id(), show.seats(labels).stream().sorted().toList(),
                        couponCode == null ? "null" : "\"" + couponCode.toLowerCase() + "\"")));
    }

    private ResultActions changeCoupon(String bookingId, String code) throws Exception {
        return mvc.perform(asCustomer(put("/api/v1/bookings/{id}/coupon", bookingId), customer)
                .content("{\"code\": \"" + code + "\"}"));
    }

    private static String bookingId(ResultActions hold) throws Exception {
        return JsonPath.read(hold.andReturn().getResponse().getContentAsString(), "$.bookingId");
    }

    private int usedCount(String code) {
        return jdbc.sql("SELECT used_count FROM coupon WHERE code = ?").param(code).query(Integer.class).single();
    }
}
