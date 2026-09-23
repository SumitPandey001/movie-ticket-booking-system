package com.sumit.movieticketbookingsystem.pricing.internal;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
class DayOfWeekRule implements PricingRule {

    private final DayRuleRepository dayRules;

    DayOfWeekRule(DayRuleRepository dayRules) {
        this.dayRules = dayRules;
    }

    @Override
    public void apply(PricingContext context) {
        dayRules.ruleFor(context.show()).ifPresent(rule -> {
            context.dayRule(rule.name());
            context.lines().forEach(line -> line.dayAdjustment = rule.adjustment(line.tier));
        });
    }
}
