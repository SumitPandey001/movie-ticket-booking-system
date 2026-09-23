package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Limits where a coupon works, e.g. (CITY, 1). Scopes of the same type are alternatives; different types must
 * all match. {@code CATEGORY} means the order must contain a seat of that category.
 */
@Embeddable
public record CouponScope(@Enumerated(EnumType.STRING) Type scopeType, long scopeId) {

    public enum Type {
        CITY,
        THEATER,
        MOVIE,
        CATEGORY
    }
}
