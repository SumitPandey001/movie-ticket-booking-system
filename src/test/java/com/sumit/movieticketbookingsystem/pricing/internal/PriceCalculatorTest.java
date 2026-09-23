package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.pricing.PriceQuote;
import com.sumit.movieticketbookingsystem.pricing.PricingRequest;
import com.sumit.movieticketbookingsystem.pricing.SeatPriceLine;
import com.sumit.movieticketbookingsystem.pricing.SeatToPrice;
import com.sumit.movieticketbookingsystem.pricing.ShowPricing;
import com.sumit.movieticketbookingsystem.pricing.internal.coupon.CouponService;
import com.sumit.movieticketbookingsystem.shared.TestBookingProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceCalculatorTest {

    private static final ShowPricing SHOW = new ShowPricing(7, 42, 1, 3, LocalDate.parse("2026-10-03"));
    private static final long REGULAR = 1;
    private static final long PREMIUM = 2;

    private final PriceRepository prices = mock(PriceRepository.class);
    private final DayRuleRepository dayRules = mock(DayRuleRepository.class);
    private final CouponService couponService = mock(CouponService.class);
    private final PriceCalculator calculator = new PriceCalculator(List.of(
            new BaseTierPriceRule(prices),
            new DayOfWeekRule(dayRules),
            new DiscountRule(couponService),
            new ConvenienceFeeRule(TestBookingProperties.defaults()),     // ₹20 per seat
            new GstRule(TestBookingProperties.defaults())));              // 18%

    @Test
    void tierPlusFeePlusGstPerSeat() {
        when(prices.showPrices(SHOW.showId())).thenReturn(Map.of(REGULAR, 20000L, PREMIUM, 30000L));
        when(dayRules.ruleFor(SHOW)).thenReturn(Optional.empty());

        PriceQuote quote = calculator.quote(
                request(List.of(new SeatToPrice(11, REGULAR), new SeatToPrice(21, PREMIUM))));

        SeatPriceLine regular = quote.lines().get(0);
        assertThat(regular.tierPaise()).isEqualTo(20000);
        assertThat(regular.dayAdjustmentPaise()).isZero();
        assertThat(regular.feePaise()).isEqualTo(2000);
        assertThat(regular.ticketTaxPaise()).isEqualTo(3600);
        assertThat(regular.feeTaxPaise()).isEqualTo(360);
        assertThat(regular.amountPaise()).isEqualTo(25960);
        assertThat(quote.lines().get(1).amountPaise()).isEqualTo(30000 + 2000 + 5400 + 360);

        assertThat(quote.subtotalPaise()).isEqualTo(50000);
        assertThat(quote.feePaise()).isEqualTo(4000);
        assertThat(quote.taxPaise()).isEqualTo(3600 + 360 + 5400 + 360);
        assertThat(quote.totalPaise()).isEqualTo(quote.lines().stream().mapToLong(SeatPriceLine::amountPaise).sum());
        assertThat(quote.dayRule()).isNull();
    }

    @Test
    void weekendSurchargeIsPartOfTheBaseAndTaxedWithIt() {
        // the LLD worked example without the coupon: ₹300 premium seat on a Saturday
        when(prices.showPrices(SHOW.showId())).thenReturn(Map.of(PREMIUM, 30000L));
        when(dayRules.ruleFor(SHOW)).thenReturn(Optional.of(new DayRule("Weekend +20%", AdjustmentType.PERCENT, 20)));

        PriceQuote quote = calculator.quote(request(List.of(new SeatToPrice(21, PREMIUM))));

        SeatPriceLine line = quote.lines().get(0);
        assertThat(line.dayAdjustmentPaise()).isEqualTo(6000);
        assertThat(line.basePaise()).isEqualTo(36000);
        assertThat(line.ticketTaxPaise()).isEqualTo(6480);                 // 18% of 36000
        assertThat(line.amountPaise()).isEqualTo(36000 + 2000 + 6480 + 360);
        assertThat(quote.subtotalPaise()).isEqualTo(36000);
        assertThat(quote.dayRule()).isEqualTo("Weekend +20%");
    }

    @Test
    void lldWorkedExampleTotals83780() {
        // two ₹300 premium seats on a Saturday (+20%) with FIRST50 (₹50 off), fee ₹20 a seat, GST 18%
        when(prices.showPrices(SHOW.showId())).thenReturn(Map.of(PREMIUM, 30000L));
        when(dayRules.ruleFor(SHOW)).thenReturn(Optional.of(new DayRule("Weekend +20%", AdjustmentType.PERCENT, 20)));
        when(couponService.evaluate(eq("FIRST50"), any())).thenReturn(new CouponService.Evaluation(1, "FIRST50", 5000));

        PriceQuote quote = calculator.quote(new PricingRequest(SHOW, UUID.randomUUID(),
                List.of(new SeatToPrice(21, PREMIUM), new SeatToPrice(22, PREMIUM)), "FIRST50"));

        assertThat(quote.lines()).allSatisfy(line -> {
            assertThat(line.tierPaise()).isEqualTo(30000);
            assertThat(line.dayAdjustmentPaise()).isEqualTo(6000);
            assertThat(line.discountPaise()).isEqualTo(2500);
            assertThat(line.ticketTaxPaise()).isEqualTo(6030);              // 18% of 33500
            assertThat(line.feePaise() + line.feeTaxPaise()).isEqualTo(2360);
            assertThat(line.amountPaise()).isEqualTo(41890);
        });
        assertThat(quote.subtotalPaise()).isEqualTo(72000);
        assertThat(quote.discountPaise()).isEqualTo(5000);
        assertThat(quote.feePaise()).isEqualTo(4000);
        assertThat(quote.taxPaise()).isEqualTo(12780);
        assertThat(quote.totalPaise()).isEqualTo(83780);
        assertThat(quote.coupon()).isEqualTo("FIRST50");
    }

    @Test
    void anUnevenDiscountIsSplitByPriceAndStillAddsUp() {
        when(prices.showPrices(SHOW.showId())).thenReturn(Map.of(REGULAR, 20000L, PREMIUM, 30000L));
        when(dayRules.ruleFor(SHOW)).thenReturn(Optional.empty());
        when(couponService.evaluate(eq("ODD"), any())).thenReturn(new CouponService.Evaluation(1, "ODD", 1001));

        PriceQuote quote = calculator.quote(new PricingRequest(SHOW, UUID.randomUUID(),
                List.of(new SeatToPrice(11, REGULAR), new SeatToPrice(21, PREMIUM)), "ODD"));

        assertThat(quote.lines()).extracting(SeatPriceLine::discountPaise).containsExactly(400L, 601L);
        assertThat(quote.discountPaise()).isEqualTo(1001);
    }

    @Test
    void flatSurchargeIsAddedPerSeat() {
        when(prices.showPrices(SHOW.showId())).thenReturn(Map.of(REGULAR, 20000L));
        when(dayRules.ruleFor(SHOW)).thenReturn(Optional.of(new DayRule("Friday night", AdjustmentType.FLAT, 5000)));

        PriceQuote quote = calculator.quote(
                request(List.of(new SeatToPrice(11, REGULAR), new SeatToPrice(12, REGULAR))));

        assertThat(quote.lines()).allSatisfy(line -> assertThat(line.basePaise()).isEqualTo(25000));
    }

    @Test
    void gstRoundsHalfUpPerSeat() {
        when(prices.showPrices(SHOW.showId())).thenReturn(Map.of(REGULAR, 33333L));   // 18% = 5999.94
        when(dayRules.ruleFor(SHOW)).thenReturn(Optional.empty());

        PriceQuote quote = calculator.quote(request(List.of(new SeatToPrice(11, REGULAR))));

        assertThat(quote.lines().get(0).ticketTaxPaise()).isEqualTo(6000);
    }

    private static PricingRequest request(List<SeatToPrice> seats) {
        return new PricingRequest(SHOW, UUID.randomUUID(), seats, null);
    }

    @Test
    void everyCategoryNeedsAShowPrice() {
        when(prices.showPrices(SHOW.showId())).thenReturn(Map.of(REGULAR, 20000L));

        assertThatThrownBy(() -> calculator.quote(request(List.of(new SeatToPrice(21, PREMIUM)))))
                .isInstanceOf(IllegalStateException.class);
    }
}
