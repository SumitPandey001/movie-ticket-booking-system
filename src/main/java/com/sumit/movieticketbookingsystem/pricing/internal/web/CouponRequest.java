package com.sumit.movieticketbookingsystem.pricing.internal.web;

import com.sumit.movieticketbookingsystem.pricing.internal.coupon.Coupon.Terms;
import com.sumit.movieticketbookingsystem.pricing.internal.coupon.CouponScope;
import com.sumit.movieticketbookingsystem.pricing.internal.coupon.DiscountType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Amounts in paise. {@code code} is required when creating and can't change afterwards (leave it out when
 * updating). {@code maxUses} empty = unlimited; {@code scopes} empty = valid everywhere.
 */
record CouponRequest(
        @Size(max = 30) String code,
        @NotNull DiscountType discountType,
        @NotNull @Positive Long discountValue,
        @Positive Long maxDiscountPaise,
        @PositiveOrZero Long minOrderPaise,
        @NotNull OffsetDateTime validFrom,
        @NotNull OffsetDateTime validTo,
        @Positive Integer maxUses,
        @Min(1) Integer perUserLimit,
        Set<@NotNull CouponScope> scopes) {

    Terms toTerms() {
        return new Terms(discountType, discountValue, maxDiscountPaise, minOrderPaise == null ? 0 : minOrderPaise,
                validFrom.toInstant(), validTo.toInstant(), maxUses, perUserLimit == null ? 1 : perUserLimit,
                scopes == null ? Set.of() : scopes);
    }
}
