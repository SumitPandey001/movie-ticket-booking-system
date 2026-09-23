package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.pricing.PriceQuote;
import com.sumit.movieticketbookingsystem.pricing.SeatPriceLine;
import com.sumit.movieticketbookingsystem.pricing.SeatToPrice;
import com.sumit.movieticketbookingsystem.pricing.ShowPricing;
import com.sumit.movieticketbookingsystem.shared.TestBookingProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceCalculatorTest {

    private static final ShowPricing SHOW = new ShowPricing(7, 1, 3, LocalDate.parse("2026-10-03"));
    private static final long REGULAR = 1;
    private static final long PREMIUM = 2;

    private final PriceRepository prices = mock(PriceRepository.class);
    private final DayRuleRepository dayRules = mock(DayRuleRepository.class);
    private final PriceCalculator calculator = new PriceCalculator(List.of(
            new BaseTierPriceRule(prices),
            new DayOfWeekRule(dayRules),
            new ConvenienceFeeRule(TestBookingProperties.defaults()),     // ₹20 per seat
            new GstRule(TestBookingProperties.defaults())));              // 18%

    @Test
    void tierPlusFeePlusGstPerSeat() {
        when(prices.showPrices(SHOW.showId())).thenReturn(Map.of(REGULAR, 20000L, PREMIUM, 30000L));
        when(dayRules.ruleFor(SHOW)).thenReturn(Optional.empty());

        PriceQuote quote = calculator.quote(SHOW, List.of(new SeatToPrice(11, REGULAR), new SeatToPrice(21, PREMIUM)));

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

        PriceQuote quote = calculator.quote(SHOW, List.of(new SeatToPrice(21, PREMIUM)));

        SeatPriceLine line = quote.lines().get(0);
        assertThat(line.dayAdjustmentPaise()).isEqualTo(6000);
        assertThat(line.basePaise()).isEqualTo(36000);
        assertThat(line.ticketTaxPaise()).isEqualTo(6480);                 // 18% of 36000
        assertThat(line.amountPaise()).isEqualTo(36000 + 2000 + 6480 + 360);
        assertThat(quote.subtotalPaise()).isEqualTo(36000);
        assertThat(quote.dayRule()).isEqualTo("Weekend +20%");
    }

    @Test
    void flatSurchargeIsAddedPerSeat() {
        when(prices.showPrices(SHOW.showId())).thenReturn(Map.of(REGULAR, 20000L));
        when(dayRules.ruleFor(SHOW)).thenReturn(Optional.of(new DayRule("Friday night", AdjustmentType.FLAT, 5000)));

        PriceQuote quote = calculator.quote(SHOW, List.of(new SeatToPrice(11, REGULAR), new SeatToPrice(12, REGULAR)));

        assertThat(quote.lines()).allSatisfy(line -> assertThat(line.basePaise()).isEqualTo(25000));
    }

    @Test
    void gstRoundsHalfUpPerSeat() {
        when(prices.showPrices(SHOW.showId())).thenReturn(Map.of(REGULAR, 33333L));   // 18% = 5999.94
        when(dayRules.ruleFor(SHOW)).thenReturn(Optional.empty());

        PriceQuote quote = calculator.quote(SHOW, List.of(new SeatToPrice(11, REGULAR)));

        assertThat(quote.lines().get(0).ticketTaxPaise()).isEqualTo(6000);
    }

    @Test
    void everyCategoryNeedsAShowPrice() {
        when(prices.showPrices(SHOW.showId())).thenReturn(Map.of(REGULAR, 20000L));

        assertThatThrownBy(() -> calculator.quote(SHOW, List.of(new SeatToPrice(21, PREMIUM))))
                .isInstanceOf(IllegalStateException.class);
    }
}
