package com.sumit.movieticketbookingsystem.pricing;

import java.util.Map;
import java.util.Set;

/**
 * Category prices per show. Prices are copied onto the show when it's created, so later changes to a theater's
 * defaults never touch shows already scheduled. Amounts are in paise; category codes are e.g. "PREMIUM".
 */
public interface PricingApi {

    /**
     * Gives the show a price for each of its seat categories: the theater's default, or the admin's override.
     *
     * @return the lowest of those prices, for "from ₹..." on the browse page
     */
    long initializeShowPrices(long showId, long theaterId, Set<Long> categoryIds, Map<String, Long> overrides);

    /**
     * Changes some of the show's category prices.
     *
     * @return the show's new lowest price
     */
    long overrideShowPrices(long showId, Map<String, Long> prices);
}
