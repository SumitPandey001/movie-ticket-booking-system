package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import com.sumit.movieticketbookingsystem.shared.Money;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(20)
class MinOrderValueRule implements CouponRule {

    @Override
    public Optional<String> violation(Coupon coupon, CouponContext context) {
        if (context.orderPaise() < coupon.getMinOrderPaise()) {
            return Optional.of("This coupon needs an order of at least "
                    + Money.ofPaise(coupon.getMinOrderPaise()).inRupees());
        }
        return Optional.empty();
    }
}
