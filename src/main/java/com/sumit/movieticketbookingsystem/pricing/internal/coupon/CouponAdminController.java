package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import com.sumit.movieticketbookingsystem.pricing.internal.coupon.Coupon.Terms;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/coupons")
class CouponAdminController {

    private final CouponAdminService couponService;

    CouponAdminController(CouponAdminService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    List<CouponResponse> coupons() {
        return couponService.coupons().stream().map(CouponResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CouponResponse create(@Valid @RequestBody CouponRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new ValidationException("A new coupon needs a code");
        }
        return CouponResponse.from(couponService.create(request.code(), request.toTerms()));
    }

    @PutMapping("/{id}")
    CouponResponse update(@PathVariable long id, @Valid @RequestBody CouponRequest request) {
        return CouponResponse.from(couponService.update(id, request.code(), request.toTerms()));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        couponService.deactivate(id);
    }

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
}
