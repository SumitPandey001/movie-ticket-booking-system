package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.pricing.PriceQuote;
import com.sumit.movieticketbookingsystem.pricing.SeatToPrice;
import com.sumit.movieticketbookingsystem.pricing.ShowPricing;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class PriceCalculator {

    private final List<PricingRule> rules;

    // Spring hands the rules over sorted by their @Order
    PriceCalculator(List<PricingRule> rules) {
        this.rules = rules;
    }

    PriceQuote quote(ShowPricing show, List<SeatToPrice> seats) {
        PricingContext context = new PricingContext(show, seats);
        rules.forEach(rule -> rule.apply(context));
        return PriceQuote.of(context.lines().stream().map(PricingContext.Line::toPriceLine).toList(),
                context.dayRule());
    }
}
