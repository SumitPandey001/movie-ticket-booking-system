package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Early feedback only: two customers can both pass this check for the last use. The real limit is the atomic
 * update when the coupon is reserved.
 */
@Component
@Order(50)
class GlobalLimitRule implements CouponRule {

    @Override
    public Optional<String> violation(Coupon coupon, CouponContext context) {
        if (coupon.getMaxUses() != null && coupon.getUsedCount() >= coupon.getMaxUses()) {
            return Optional.of("This coupon has been fully used");
        }
        return Optional.empty();
    }
}
