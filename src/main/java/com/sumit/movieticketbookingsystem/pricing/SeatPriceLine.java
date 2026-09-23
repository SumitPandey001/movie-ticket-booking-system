package com.sumit.movieticketbookingsystem.pricing;

/**
 * One seat's price, in paise.
 *
 * @param dayAdjustmentPaise day-of-week surcharge on top of the tier price
 * @param feePaise           convenience fee, before GST
 * @param ticketTaxPaise     GST on the ticket (tier + adjustment − discount)
 * @param feeTaxPaise        GST on the convenience fee
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
