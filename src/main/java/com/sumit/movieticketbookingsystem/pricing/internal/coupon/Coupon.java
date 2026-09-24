package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import com.sumit.movieticketbookingsystem.shared.persistence.AuditedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Coupon extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    private long discountValue;

    private Long maxDiscountPaise;

    private long minOrderPaise;

    private Instant validFrom;

    private Instant validTo;

    private Integer maxUses;

    @Column(insertable = false, updatable = false)
    private int usedCount;

    private int perUserLimit;

    private boolean active = true;

    @ElementCollection
    @CollectionTable(name = "coupon_scope", joinColumns = @JoinColumn(name = "coupon_id"))
    private Set<CouponScope> scopes = new HashSet<>();

    protected Coupon() {
    }

    public Coupon(String code, Terms terms) {
        this.code = code;
        update(terms);
    }

    public void update(Terms terms) {
        this.discountType = terms.discountType();
        this.discountValue = terms.discountValue();
        this.maxDiscountPaise = terms.maxDiscountPaise();
        this.minOrderPaise = terms.minOrderPaise();
        this.validFrom = terms.validFrom();
        this.validTo = terms.validTo();
        this.maxUses = terms.maxUses();
        this.perUserLimit = terms.perUserLimit();
        this.scopes.clear();
        this.scopes.addAll(terms.scopes());
    }

    public void deactivate() {
        this.active = false;
    }

    public record Terms(DiscountType discountType, long discountValue, Long maxDiscountPaise, long minOrderPaise,
                        Instant validFrom, Instant validTo, Integer maxUses, int perUserLimit,
                        Set<CouponScope> scopes) {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public long getDiscountValue() {
        return discountValue;
    }

    public Long getMaxDiscountPaise() {
        return maxDiscountPaise;
    }

    public long getMinOrderPaise() {
        return minOrderPaise;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public int getPerUserLimit() {
        return perUserLimit;
    }

    public boolean isActive() {
        return active;
    }

    public Set<CouponScope> getScopes() {
        return Set.copyOf(scopes);
    }
}
