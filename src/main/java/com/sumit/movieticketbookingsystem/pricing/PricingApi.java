package com.sumit.movieticketbookingsystem.pricing;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Category prices per show. Prices are copied onto the show when it's created, so later changes to a theater's
 * defaults never touch shows already scheduled. Day-of-week rules are applied on top whenever a price is shown
 * or quoted. Amounts are in paise; category codes are e.g. "PREMIUM".
 */
public interface PricingApi {

    /**
     * Gives the show a price for each of its seat categories: the theater's default, or the admin's override.
     *
     * @return the lowest price including the day's surcharge, for "from ₹..." on the browse page
     */
    long initializeShowPrices(ShowPricing show, Set<Long> categoryIds, Map<String, Long> overrides);

    /**
     * Changes some of the show's category prices.
     *
     * @return the show's new lowest price including the day's surcharge
     */
    long overrideShowPrices(ShowPricing show, Map<String, Long> prices);

    /** Price per seat category (by category id) as the customer sees it: tier plus the day's surcharge. */
    Map<Long, Long> displayPrices(ShowPricing show);

    /** Prices the given seats: tier, day surcharge, convenience fee and GST, one line per seat. */
    PriceQuote quote(ShowPricing show, List<SeatToPrice> seats);
}
