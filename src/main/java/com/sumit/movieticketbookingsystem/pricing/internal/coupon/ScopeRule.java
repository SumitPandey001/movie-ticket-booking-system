package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A coupon without scopes works everywhere. Otherwise the order must match every scope type the coupon has,
 * and any one of the ids listed for that type.
 */
@Component
@Order(30)
class ScopeRule implements CouponRule {

    @Override
    public Optional<String> violation(Coupon coupon, CouponContext context) {
        Map<CouponScope.Type, Set<Long>> allowed = coupon.getScopes().stream()
                .collect(Collectors.groupingBy(CouponScope::scopeType,
                        Collectors.mapping(CouponScope::scopeId, Collectors.toSet())));
        for (Map.Entry<CouponScope.Type, Set<Long>> scope : allowed.entrySet()) {
            Set<Long> ids = scope.getValue();
            boolean matches = switch (scope.getKey()) {
                case CITY -> ids.contains(context.show().cityId());
                case THEATER -> ids.contains(context.show().theaterId());
                case MOVIE -> ids.contains(context.show().movieId());
                case CATEGORY -> context.categoryIds().stream().anyMatch(ids::contains);
            };
            if (!matches) {
                return Optional.of("This coupon can't be used for this show");
            }
        }
        return Optional.empty();
    }
}
