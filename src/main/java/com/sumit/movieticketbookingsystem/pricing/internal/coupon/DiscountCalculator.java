package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

/**
 * How much a coupon of one {@link DiscountType} takes off an order (Strategy).
 */
interface DiscountCalculator {

    DiscountType type();

    /** The discount in paise; never more than the order itself. */
    long discount(Coupon coupon, long orderPaise);
}
