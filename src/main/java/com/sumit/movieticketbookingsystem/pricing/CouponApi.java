package com.sumit.movieticketbookingsystem.pricing;

import java.util.UUID;

/**
 * Using coupons up. Both calls join the caller's transaction, so a failed hold also undoes its coupon.
 */
public interface CouponApi {

    /**
     * Takes one use of the coupon for the booking, within its total and per-customer limits. Throws
     * CouponInvalidException if either limit has been reached, or the coupon was deactivated in the meantime.
     */
    void reserve(String code, UUID userId, UUID bookingId, long discountPaise);

    /** Marks the booking's reserved coupon as used for good, once the booking is paid. No-op without a coupon. */
    void consume(UUID bookingId);

    /** Gives the booking's coupon use back, if it has one. */
    void release(UUID bookingId);
}
