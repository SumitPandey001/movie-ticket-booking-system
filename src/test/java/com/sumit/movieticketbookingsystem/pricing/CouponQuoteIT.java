package com.sumit.movieticketbookingsystem.pricing;

import com.sumit.movieticketbookingsystem.TestDates;
import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import com.sumit.movieticketbookingsystem.catalog.CatalogFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The LLD worked example against the real database: two ₹300 premium seats on a Saturday with FIRST50.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CouponQuoteIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PricingApi pricing;

    private CouponFixtures coupons;
    private BookableShow saturday;
    private ShowPricing show;

    @BeforeEach
    void setUp() throws Exception {
        coupons = new CouponFixtures(mvc);
        saturday = new BookingFixtures(mvc, jdbc).openShowOn(TestDates.saturday(2));
        show = jdbc.sql("SELECT id, movie_id, city_id, theater_id, show_date FROM show WHERE id = ?")
                .param(saturday.id())
                .query((rs, row) -> new ShowPricing(rs.getLong("id"), rs.getLong("movie_id"), rs.getLong("city_id"),
                        rs.getLong("theater_id"), rs.getDate("show_date").toLocalDate()))
                .single();
    }

    @Test
    void workedExampleTotals83780() throws Exception {
        String code = coupons.coupon("""
                "discountType": "FLAT", "discountValue": 5000, "minOrderPaise": 30000,
                "scopes": [{"scopeType": "CITY", "scopeId": %d}]""".formatted(show.cityId()));

        PriceQuote quote = pricing.quote(request(code.toLowerCase(), "B1", "B2"));

        assertThat(quote.lines()).extracting(SeatPriceLine::amountPaise).containsExactly(41890L, 41890L);
        assertThat(quote.totalPaise()).isEqualTo(83780);
        assertThat(quote.dayRule()).isEqualTo("Weekend +20%");
        assertThat(quote.coupon()).isEqualTo(code);
    }

    @Test
    void couponThatDoesntFitTheOrderIsRefusedWithAReason() throws Exception {
        long otherCityId = new CatalogFixtures(mvc).city();
        String otherCity = coupons.coupon("""
                "discountType": "FLAT", "discountValue": 5000,
                "scopes": [{"scopeType": "CITY", "scopeId": %d}]""".formatted(otherCityId));
        String bigOrdersOnly = coupons.coupon("""
                "discountType": "PERCENT", "discountValue": 10, "minOrderPaise": 100000, "scopes": []""");

        assertThatThrownBy(() -> pricing.quote(request(otherCity, "B1")))
                .isInstanceOf(CouponInvalidException.class)
                .hasMessage("This coupon can't be used for this show");
        assertThatThrownBy(() -> pricing.quote(request(bigOrdersOnly, "B1")))
                .isInstanceOf(CouponInvalidException.class)
                .hasMessage("This coupon needs an order of at least ₹1000");
        assertThatThrownBy(() -> pricing.quote(request("NOSUCHCODE", "B1")))
                .isInstanceOf(CouponInvalidException.class)
                .hasMessage("There's no coupon NOSUCHCODE");
    }

    private PricingRequest request(String code, String... labels) {
        List<SeatToPrice> seats = saturday.seats(labels).stream().sorted()
                .map(id -> new SeatToPrice(id, jdbc.sql("SELECT category_id FROM show_seat WHERE show_id = ? "
                        + "AND layout_seat_id = ?").params(saturday.id(), id).query(Long.class).single()))
                .toList();
        return new PricingRequest(show, UUID.randomUUID(), seats, code);
    }
}
