package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.catalog.CatalogApi;
import com.sumit.movieticketbookingsystem.pricing.internal.DayPricingRule.Definition;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class DayPricingRuleService {

    private static final Logger log = LoggerFactory.getLogger(DayPricingRuleService.class);

    private static final int MAX_PERCENT = 100;

    private final DayPricingRuleRepository rules;
    private final CatalogApi catalog;

    DayPricingRuleService(DayPricingRuleRepository rules, CatalogApi catalog) {
        this.rules = rules;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<DayPricingRule> rules() {
        return rules.findAllByOrderById();
    }

    @Transactional
    public DayPricingRule create(Definition definition) {
        validate(definition);
        DayPricingRule rule = rules.save(new DayPricingRule(definition));
        log.info("Pricing rule {} created: {}", rule.getId(), definition);
        return rule;
    }

    @Transactional
    public DayPricingRule update(long id, Definition definition) {
        validate(definition);
        DayPricingRule rule = find(id);
        rule.update(definition);
        log.info("Pricing rule {} updated: {}", id, definition);
        return rule;
    }

    @Transactional
    public void delete(long id) {
        rules.delete(find(id));
        log.info("Pricing rule {} deleted", id);
    }

    private void validate(Definition definition) {
        switch (definition.scope()) {
            case GLOBAL -> {
                if (definition.scopeId() != null) {
                    throw new ValidationException("A GLOBAL rule has no scopeId");
                }
            }
            case CITY -> catalog.city(requireScopeId(definition));
            case THEATER -> {
                long theaterId = requireScopeId(definition);
                if (catalog.theaters(Set.of(theaterId)).isEmpty()) {
                    throw new NotFoundException("Theater", theaterId);
                }
            }
        }
        if (definition.adjustmentType() == AdjustmentType.PERCENT && definition.adjustmentValue() > MAX_PERCENT) {
            throw new ValidationException("A percentage surcharge can be at most " + MAX_PERCENT + "%");
        }
        if (definition.validFrom() != null && definition.validTo() != null
                && definition.validTo().isBefore(definition.validFrom())) {
            throw new ValidationException("validTo is before validFrom");
        }
    }

    private static long requireScopeId(Definition definition) {
        if (definition.scopeId() == null) {
            throw new ValidationException("A " + definition.scope() + " rule needs a scopeId");
        }
        return definition.scopeId();
    }

    private DayPricingRule find(long id) {
        return rules.findById(id).orElseThrow(() -> new NotFoundException("Pricing rule", id));
    }
}
