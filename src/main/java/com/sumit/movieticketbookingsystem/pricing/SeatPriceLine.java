package com.sumit.movieticketbookingsystem.pricing;

/**
 * One seat's price, in paise.
 *
 * @param feePaise      convenience fee, before GST
 * @param ticketTaxPaise GST on the ticket after discount
 * @param feeTaxPaise    GST on the convenience fee
 */
public record SeatPriceLine(long layoutSeatId, long categoryId, long tierPaise, long discountPaise, long feePaise,
                            long ticketTaxPaise, long feeTaxPaise) {

    public long amountPaise() {
        return tierPaise - discountPaise + feePaise + ticketTaxPaise + feeTaxPaise;
    }
}
