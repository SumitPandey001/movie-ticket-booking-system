package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.shared.persistence.AuditedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * An admin-defined day-of-week surcharge (a row of pricing_rule). {@link DayRuleRepository} picks the one that
 * applies to a show; this class is for managing them.
 */
@Entity
@Table(name = "pricing_rule")
class DayPricingRule extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private RuleScope scopeType;

    private Long scopeId;

    private Short[] daysOfWeek;

    @Enumerated(EnumType.STRING)
    private AdjustmentType adjustmentType;

    private long adjustmentValue;

    private LocalDate validFrom;

    private LocalDate validTo;

    private boolean active;

    protected DayPricingRule() {
    }

    DayPricingRule(Definition definition) {
        update(definition);
    }

    void update(Definition definition) {
        this.name = definition.name();
        this.scopeType = definition.scope();
        this.scopeId = definition.scopeId();
        this.daysOfWeek = definition.daysOfWeek().stream().sorted().map(Integer::shortValue).toArray(Short[]::new);
        this.adjustmentType = definition.adjustmentType();
        this.adjustmentValue = definition.adjustmentValue();
        this.validFrom = definition.validFrom();
        this.validTo = definition.validTo();
        this.active = definition.active();
    }

    /** Everything an admin sets on a rule; already validated by the service. */
    record Definition(String name, RuleScope scope, Long scopeId, Set<Integer> daysOfWeek,
                      AdjustmentType adjustmentType, long adjustmentValue, LocalDate validFrom, LocalDate validTo,
                      boolean active) {
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    RuleScope getScopeType() {
        return scopeType;
    }

    Long getScopeId() {
        return scopeId;
    }

    List<Integer> getDaysOfWeek() {
        return Arrays.stream(daysOfWeek).map(Short::intValue).toList();
    }

    AdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    long getAdjustmentValue() {
        return adjustmentValue;
    }

    LocalDate getValidFrom() {
        return validFrom;
    }

    LocalDate getValidTo() {
        return validTo;
    }

    boolean isActive() {
        return active;
    }
}
