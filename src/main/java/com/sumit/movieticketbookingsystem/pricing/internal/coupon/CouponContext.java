package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import com.sumit.movieticketbookingsystem.pricing.ShowPricing;

import java.util.Set;
import java.util.UUID;

/**
 * The order a coupon is being tried on.
 *
 * @param orderPaise  seat prices including day surcharges, before fees and tax; what the discount applies to
 * @param categoryIds the seat categories in the order
 */
public record CouponContext(ShowPricing show, UUID userId, long orderPaise, Set<Long> categoryIds) {
}
