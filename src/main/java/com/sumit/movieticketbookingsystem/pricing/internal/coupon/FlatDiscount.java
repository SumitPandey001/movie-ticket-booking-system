package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import org.springframework.stereotype.Component;

@Component
class FlatDiscount implements DiscountCalculator {

    @Override
    public DiscountType type() {
        return DiscountType.FLAT;
    }

    @Override
    public long discount(Coupon coupon, long orderPaise) {
        return Math.min(coupon.getDiscountValue(), orderPaise);
    }
}
