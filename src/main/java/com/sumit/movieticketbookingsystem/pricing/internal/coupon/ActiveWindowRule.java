package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component
@Order(10)
class ActiveWindowRule implements CouponRule {

    private final Clock clock;

    ActiveWindowRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<String> violation(Coupon coupon, CouponContext context) {
        Instant now = Instant.now(clock);
        if (!coupon.isActive() || !now.isBefore(coupon.getValidTo())) {
            return Optional.of("This coupon has expired");
        }
        if (now.isBefore(coupon.getValidFrom())) {
            return Optional.of("This coupon isn't valid yet");
        }
        return Optional.empty();
    }
}
