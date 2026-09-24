package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import java.util.Optional;

/**
 * One condition a coupon must meet. Rules are checked in their Order and the first one
 * that fails decides the message the customer sees.
 */
interface CouponRule {

    /** Why the coupon can't be used for this order, or empty if this rule is satisfied. */
    Optional<String> violation(Coupon coupon, CouponContext context);
}
