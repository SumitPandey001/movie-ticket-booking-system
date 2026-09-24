package com.sumit.movieticketbookingsystem.pricing.internal.web;

import com.sumit.movieticketbookingsystem.pricing.internal.AdjustmentType;
import com.sumit.movieticketbookingsystem.pricing.internal.DayPricingRule;
import com.sumit.movieticketbookingsystem.pricing.internal.RuleScope;

import java.time.LocalDate;
import java.util.List;

record PricingRuleResponse(long id, String name, RuleScope scopeType, Long scopeId, List<Integer> daysOfWeek,
                    AdjustmentType adjustmentType, long adjustmentValue, LocalDate validFrom, LocalDate validTo,
                    boolean active) {

    static PricingRuleResponse from(DayPricingRule rule) {
        return new PricingRuleResponse(rule.getId(), rule.getName(), rule.getScopeType(), rule.getScopeId(),
                rule.getDaysOfWeek(), rule.getAdjustmentType(), rule.getAdjustmentValue(), rule.getValidFrom(),
                rule.getValidTo(), rule.isActive());
    }
}
