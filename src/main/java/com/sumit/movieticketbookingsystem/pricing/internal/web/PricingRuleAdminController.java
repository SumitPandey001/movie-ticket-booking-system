package com.sumit.movieticketbookingsystem.pricing.internal.web;

import com.sumit.movieticketbookingsystem.pricing.internal.DayPricingRuleService;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/pricing-rules")
class PricingRuleAdminController {

    private final DayPricingRuleService ruleService;

    PricingRuleAdminController(DayPricingRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    List<PricingRuleResponse> rules() {
        return ruleService.rules().stream().map(PricingRuleResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PricingRuleResponse create(@Valid @RequestBody PricingRuleRequest request) {
        return PricingRuleResponse.from(ruleService.create(request.toDefinition()));
    }

    @PutMapping("/{id}")
    PricingRuleResponse update(@PathVariable long id, @Valid @RequestBody PricingRuleRequest request) {
        return PricingRuleResponse.from(ruleService.update(id, request.toDefinition()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        ruleService.delete(id);
    }
}
