package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.pricing.PriceQuote;
import com.sumit.movieticketbookingsystem.pricing.SeatPriceLine;
import com.sumit.movieticketbookingsystem.pricing.SeatToPrice;
import com.sumit.movieticketbookingsystem.shared.TestBookingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceCalculatorTest {

    private static final long SHOW = 7;
    private static final long REGULAR = 1;
    private static final long PREMIUM = 2;

    private final PriceRepository prices = mock(PriceRepository.class);
    private final PriceCalculator calculator = new PriceCalculator(List.of(
            new BaseTierPriceRule(prices),
            new ConvenienceFeeRule(TestBookingProperties.defaults()),     // ₹20 per seat
            new GstRule(TestBookingProperties.defaults())));              // 18%

    @Test
    void tierPlusFeePlusGstPerSeat() {
        when(prices.showPrices(SHOW)).thenReturn(Map.of(REGULAR, 20000L, PREMIUM, 30000L));

        PriceQuote quote = calculator.quote(SHOW, List.of(new SeatToPrice(11, REGULAR), new SeatToPrice(21, PREMIUM)));

        SeatPriceLine regular = quote.lines().get(0);
        assertThat(regular.tierPaise()).isEqualTo(20000);
        assertThat(regular.feePaise()).isEqualTo(2000);
        assertThat(regular.ticketTaxPaise()).isEqualTo(3600);
        assertThat(regular.feeTaxPaise()).isEqualTo(360);
        assertThat(regular.amountPaise()).isEqualTo(25960);
        assertThat(quote.lines().get(1).amountPaise()).isEqualTo(30000 + 2000 + 5400 + 360);

        assertThat(quote.subtotalPaise()).isEqualTo(50000);
        assertThat(quote.feePaise()).isEqualTo(4000);
        assertThat(quote.taxPaise()).isEqualTo(3600 + 360 + 5400 + 360);
        assertThat(quote.totalPaise()).isEqualTo(quote.lines().stream().mapToLong(SeatPriceLine::amountPaise).sum());
    }

    @Test
    void gstRoundsHalfUpPerSeat() {
        when(prices.showPrices(SHOW)).thenReturn(Map.of(REGULAR, 33333L));   // 18% = 5999.94

        PriceQuote quote = calculator.quote(SHOW, List.of(new SeatToPrice(11, REGULAR)));

        assertThat(quote.lines().get(0).ticketTaxPaise()).isEqualTo(6000);
    }

    @Test
    void everyCategoryNeedsAShowPrice() {
        when(prices.showPrices(SHOW)).thenReturn(Map.of(REGULAR, 20000L));

        assertThatThrownBy(() -> calculator.quote(SHOW, List.of(new SeatToPrice(21, PREMIUM))))
                .isInstanceOf(IllegalStateException.class);
    }
}
