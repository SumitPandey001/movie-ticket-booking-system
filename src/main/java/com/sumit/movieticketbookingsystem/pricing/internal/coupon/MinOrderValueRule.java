package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(20)
class MinOrderValueRule implements CouponRule {

    @Override
    public Optional<String> violation(Coupon coupon, CouponContext context) {
        if (context.orderPaise() < coupon.getMinOrderPaise()) {
            return Optional.of("This coupon needs an order of at least ₹" + rupees(coupon.getMinOrderPaise()));
        }
        return Optional.empty();
    }

    private static String rupees(long paise) {
        return paise % 100 == 0 ? String.valueOf(paise / 100) : String.format("%d.%02d", paise / 100, paise % 100);
    }
}
