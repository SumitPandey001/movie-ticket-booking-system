package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import com.sumit.movieticketbookingsystem.shared.Money;
import org.springframework.stereotype.Component;

@Component
class PercentageDiscount implements DiscountCalculator {

    @Override
    public DiscountType type() {
        return DiscountType.PERCENT;
    }

    @Override
    public long discount(Coupon coupon, long orderPaise) {
        long discount = Money.ofPaise(orderPaise).percent(Math.toIntExact(coupon.getDiscountValue())).paise();
        return coupon.getMaxDiscountPaise() == null ? discount : Math.min(discount, coupon.getMaxDiscountPaise());
    }
}
