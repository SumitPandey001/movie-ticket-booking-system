package com.sumit.movieticketbookingsystem.pricing;

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
     * Returns the lowest price including the day's surcharge, for "from ₹..." on the browse page.
     */
    long initializeShowPrices(ShowPricing show, Set<Long> categoryIds, Map<String, Long> overrides);

    /**
     * Changes some of the show's category prices and returns its new lowest price, day surcharge included.
     */
    long overrideShowPrices(ShowPricing show, Map<String, Long> prices);

    /** Price per seat category (by category id) as the customer sees it: tier plus the day's surcharge. */
    Map<Long, Long> displayPrices(ShowPricing show);

    /**
     * Prices the given seats: tier, day surcharge, coupon discount, convenience fee and GST, one line per seat.
     * Only checks the coupon; using it up is a separate step. Throws CouponInvalidException if a coupon code is
     * given but can't be used for this order.
     */
    PriceQuote quote(PricingRequest request);
}
