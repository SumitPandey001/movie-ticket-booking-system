package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The calculator for each discount type. Refuses to start if a type has none or two, so a new type can't
 * reach production half-done.
 */
@Component
class DiscountCalculatorRegistry {

    private final Map<DiscountType, DiscountCalculator> byType = new EnumMap<>(DiscountType.class);

    DiscountCalculatorRegistry(List<DiscountCalculator> calculators) {
        for (DiscountCalculator calculator : calculators) {
            if (byType.put(calculator.type(), calculator) != null) {
                throw new IllegalStateException("Two discount calculators for " + calculator.type());
            }
        }
        Set<DiscountType> missing = EnumSet.allOf(DiscountType.class);
        missing.removeAll(byType.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No discount calculator for " + missing);
        }
    }

    DiscountCalculator forType(DiscountType type) {
        return byType.get(type);
    }
}
