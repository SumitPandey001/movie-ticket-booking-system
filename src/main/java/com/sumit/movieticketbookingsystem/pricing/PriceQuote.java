package com.sumit.movieticketbookingsystem.pricing;

import java.util.List;

/**
 * What the customer will pay, seat by seat. subtotalPaise is tier prices plus day adjustments;
 * taxPaise is GST on tickets and fees together. dayRule and coupon name the surcharge and
 * the coupon applied, if any.
 */
public record PriceQuote(List<SeatPriceLine> lines, long subtotalPaise, long discountPaise, long feePaise,
                         long taxPaise, long totalPaise, String dayRule, String coupon) {

    public PriceQuote {
        lines = List.copyOf(lines);
    }

    public static PriceQuote of(List<SeatPriceLine> lines, String dayRule, String coupon) {
        long subtotal = lines.stream().mapToLong(SeatPriceLine::basePaise).sum();
        long discount = lines.stream().mapToLong(SeatPriceLine::discountPaise).sum();
        long fee = lines.stream().mapToLong(SeatPriceLine::feePaise).sum();
        long tax = lines.stream().mapToLong(line -> line.ticketTaxPaise() + line.feeTaxPaise()).sum();
        return new PriceQuote(lines, subtotal, discount, fee, tax, subtotal - discount + fee + tax, dayRule, coupon);
    }
}
