package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import com.sumit.movieticketbookingsystem.pricing.CouponInvalidException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Decides whether a coupon can be used for an order and how much it takes off. Doesn't use the coupon up.
 */
@Service
public class CouponService {

    private final CouponRepository coupons;
    private final List<CouponRule> rules;
    private final DiscountCalculatorRegistry calculators;

    CouponService(CouponRepository coupons, List<CouponRule> rules, DiscountCalculatorRegistry calculators) {
        this.coupons = coupons;
        this.rules = rules;
        this.calculators = calculators;
    }

    public record Evaluation(long couponId, String code, long discountPaise) {
    }

    // throws CouponInvalidException with the reason we show the customer
    public Evaluation evaluate(String code, CouponContext context) {
        Coupon coupon = coupons.findByCode(code.strip().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new CouponInvalidException("There's no coupon " + code.strip()));
        for (CouponRule rule : rules) {
            Optional<String> violation = rule.violation(coupon, context);
            if (violation.isPresent()) {
                throw new CouponInvalidException(violation.get());
            }
        }
        long discount = calculators.forType(coupon.getDiscountType()).discount(coupon, context.orderPaise());
        return new Evaluation(coupon.getId(), coupon.getCode(), Math.min(discount, context.orderPaise()));
    }
}
