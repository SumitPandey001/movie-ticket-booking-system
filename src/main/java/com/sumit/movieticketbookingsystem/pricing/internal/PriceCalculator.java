package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.pricing.PriceQuote;
import com.sumit.movieticketbookingsystem.pricing.PricingRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class PriceCalculator {

    private final List<PricingRule> rules;

    // Spring hands the rules over already sorted by their Order
    PriceCalculator(List<PricingRule> rules) {
        this.rules = rules;
    }

    PriceQuote quote(PricingRequest request) {
        PricingContext context = new PricingContext(request);
        rules.forEach(rule -> rule.apply(context));
        return PriceQuote.of(context.lines().stream().map(PricingContext.Line::toPriceLine).toList(),
                context.dayRule(), context.coupon());
    }
}
