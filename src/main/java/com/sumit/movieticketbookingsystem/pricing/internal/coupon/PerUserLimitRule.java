package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Early feedback only; the real limit is the conditional insert when the coupon is reserved.
 */
@Component
@Order(40)
class PerUserLimitRule implements CouponRule {

    private final CouponRedemptionRepository redemptions;

    PerUserLimitRule(CouponRedemptionRepository redemptions) {
        this.redemptions = redemptions;
    }

    @Override
    public Optional<String> violation(Coupon coupon, CouponContext context) {
        if (redemptions.usesBy(coupon.getId(), context.userId()) >= coupon.getPerUserLimit()) {
            return Optional.of("You've already used this coupon");
        }
        return Optional.empty();
    }
}
