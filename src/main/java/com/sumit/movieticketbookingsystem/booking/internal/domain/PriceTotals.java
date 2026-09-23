package com.sumit.movieticketbookingsystem.booking.internal.domain;

/**
 * A booking's totals in paise. {@code tax} covers GST on both tickets and fees.
 */
public record PriceTotals(long subtotal, long discount, long fee, long tax, long total) {

    public PriceTotals {
        if (total != subtotal - discount + fee + tax) {
            throw new IllegalArgumentException("Totals don't add up: " + subtotal + " - " + discount + " + " + fee
                    + " + " + tax + " != " + total);
        }
    }
}
