package com.sumit.movieticketbookingsystem.pricing.internal.web;

import com.sumit.movieticketbookingsystem.pricing.internal.coupon.Coupon;
import com.sumit.movieticketbookingsystem.pricing.internal.coupon.CouponScope;
import com.sumit.movieticketbookingsystem.pricing.internal.coupon.DiscountType;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

record CouponResponse(long id, String code, DiscountType discountType, long discountValue, Long maxDiscountPaise,
                      long minOrderPaise, Instant validFrom, Instant validTo, Integer maxUses, int usedCount,
                      int perUserLimit, boolean active, List<CouponScope> scopes) {

    static CouponResponse from(Coupon coupon) {
        return new CouponResponse(coupon.getId(), coupon.getCode(), coupon.getDiscountType(),
                coupon.getDiscountValue(), coupon.getMaxDiscountPaise(), coupon.getMinOrderPaise(),
                coupon.getValidFrom(), coupon.getValidTo(), coupon.getMaxUses(), coupon.getUsedCount(),
                coupon.getPerUserLimit(), coupon.isActive(),
                coupon.getScopes().stream()
                        .sorted(Comparator.comparing(CouponScope::scopeType).thenComparing(CouponScope::scopeId))
                        .toList());
    }
}
