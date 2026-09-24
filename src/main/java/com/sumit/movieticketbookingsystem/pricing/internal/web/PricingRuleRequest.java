package com.sumit.movieticketbookingsystem.pricing.internal.web;

import com.sumit.movieticketbookingsystem.pricing.internal.AdjustmentType;
import com.sumit.movieticketbookingsystem.pricing.internal.DayPricingRule.Definition;
import com.sumit.movieticketbookingsystem.pricing.internal.DayPricingRule.Definition;
import com.sumit.movieticketbookingsystem.pricing.internal.RuleScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

/**
 * e.g. {@code {"name": "Weekend +20%", "scopeType": "GLOBAL", "daysOfWeek": [6, 7],
 * "adjustmentType": "PERCENT", "adjustmentValue": 20}}. Days are ISO: 1 = Monday ... 7 = Sunday.
 * {@code active} defaults to true; set it to false to pause a rule without deleting it.
 */
record PricingRuleRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull RuleScope scopeType,
        Long scopeId,
        @NotEmpty Set<@NotNull @Min(1) @Max(7) Integer> daysOfWeek,
        @NotNull AdjustmentType adjustmentType,
        @NotNull @Positive Long adjustmentValue,
        LocalDate validFrom,
        LocalDate validTo,
        Boolean active) {

    Definition toDefinition() {
        return new Definition(name.strip(), scopeType, scopeId, daysOfWeek, adjustmentType, adjustmentValue,
                validFrom, validTo, active == null || active);
    }
}
