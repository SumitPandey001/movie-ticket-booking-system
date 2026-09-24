package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import com.sumit.movieticketbookingsystem.pricing.ShowPricing;

import java.util.Set;
import java.util.UUID;

public record CouponContext(ShowPricing show, UUID userId, long orderPaise, Set<Long> categoryIds) {
}
