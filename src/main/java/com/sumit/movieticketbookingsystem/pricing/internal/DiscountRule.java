package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.pricing.internal.coupon.CouponContext;
import com.sumit.movieticketbookingsystem.pricing.internal.coupon.CouponService;
import com.sumit.movieticketbookingsystem.shared.Allocator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Applies the customer's coupon to the order (seat prices after the day surcharge) and spreads the discount over
 * the seats in proportion to their price, so a refund for one seat later gives back exactly that seat's share.
 */
@Component
@Order(30)
class DiscountRule implements PricingRule {

    private final CouponService couponService;

    DiscountRule(CouponService couponService) {
        this.couponService = couponService;
    }

    @Override
    public void apply(PricingContext context) {
        String code = context.request().couponCode();
        if (code == null || code.isBlank()) {
            return;
        }
        List<PricingContext.Line> lines = context.lines();
        List<Long> bases = lines.stream().map(PricingContext.Line::base).toList();
        CouponContext order = new CouponContext(context.show(), context.request().userId(),
                bases.stream().mapToLong(Long::longValue).sum(),
                lines.stream().map(line -> line.categoryId).collect(Collectors.toSet()));

        CouponService.Evaluation coupon = couponService.evaluate(code, order);
        long[] shares = Allocator.largestRemainder(coupon.discountPaise(), bases);
        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).discount = shares[i];
        }
        context.coupon(coupon.code());
    }
}
