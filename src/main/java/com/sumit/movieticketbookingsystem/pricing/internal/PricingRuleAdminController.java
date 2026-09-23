package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.pricing.internal.DayPricingRule.Definition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/pricing-rules")
class PricingRuleAdminController {

    private final DayPricingRuleService ruleService;

    PricingRuleAdminController(DayPricingRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    List<RuleResponse> rules() {
        return ruleService.rules().stream().map(RuleResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RuleResponse create(@Valid @RequestBody RuleRequest request) {
        return RuleResponse.from(ruleService.create(request.toDefinition()));
    }

    @PutMapping("/{id}")
    RuleResponse update(@PathVariable long id, @Valid @RequestBody RuleRequest request) {
        return RuleResponse.from(ruleService.update(id, request.toDefinition()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        ruleService.delete(id);
    }

    /**
     * e.g. {@code {"name": "Weekend +20%", "scopeType": "GLOBAL", "daysOfWeek": [6, 7],
     * "adjustmentType": "PERCENT", "adjustmentValue": 20}}. Days are ISO: 1 = Monday ... 7 = Sunday.
     * {@code active} defaults to true; set it to false to pause a rule without deleting it.
     */
    record RuleRequest(
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

    record RuleResponse(long id, String name, RuleScope scopeType, Long scopeId, List<Integer> daysOfWeek,
                        AdjustmentType adjustmentType, long adjustmentValue, LocalDate validFrom, LocalDate validTo,
                        boolean active) {

        static RuleResponse from(DayPricingRule rule) {
            return new RuleResponse(rule.getId(), rule.getName(), rule.getScopeType(), rule.getScopeId(),
                    rule.getDaysOfWeek(), rule.getAdjustmentType(), rule.getAdjustmentValue(), rule.getValidFrom(),
                    rule.getValidTo(), rule.isActive());
        }
    }
}
