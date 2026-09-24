package com.sumit.movieticketbookingsystem.pricing;

/**
 * One seat's price, in paise. The day adjustment is the day-of-week surcharge on top of the tier price, the
 * fee is before GST, the ticket tax is GST on tier + adjustment − discount, and the fee tax is GST on the fee.
 */
public record SeatPriceLine(long layoutSeatId, long categoryId, long tierPaise, long dayAdjustmentPaise,
                            long discountPaise, long feePaise, long ticketTaxPaise, long feeTaxPaise) {

    /** The ticket price before discount: tier plus the day's surcharge. */
    public long basePaise() {
        return tierPaise + dayAdjustmentPaise;
    }

    public long amountPaise() {
        return basePaise() - discountPaise + feePaise + ticketTaxPaise + feeTaxPaise;
    }
}
