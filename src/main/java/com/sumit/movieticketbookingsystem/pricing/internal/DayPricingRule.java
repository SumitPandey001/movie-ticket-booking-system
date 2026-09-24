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
public class DayPricingRule extends AuditedEntity {

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
    public record Definition(String name, RuleScope scope, Long scopeId, Set<Integer> daysOfWeek,
                      AdjustmentType adjustmentType, long adjustmentValue, LocalDate validFrom, LocalDate validTo,
                      boolean active) {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RuleScope getScopeType() {
        return scopeType;
    }

    public Long getScopeId() {
        return scopeId;
    }

    public List<Integer> getDaysOfWeek() {
        return Arrays.stream(daysOfWeek).map(Short::intValue).toList();
    }

    public AdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public long getAdjustmentValue() {
        return adjustmentValue;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public boolean isActive() {
        return active;
    }
}
