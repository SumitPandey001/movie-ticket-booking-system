package com.sumit.movieticketbookingsystem.pricing;

import java.time.LocalDate;

/**
 * What pricing needs to know about a show. Callers pass it in so pricing never has to ask the show module.
 *
 * @param showDate the listing date, whose day of the week picks the day rule
 */
public record ShowPricing(long showId, long cityId, long theaterId, LocalDate showDate) {
}
