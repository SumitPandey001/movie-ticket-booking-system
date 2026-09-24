package com.sumit.movieticketbookingsystem.booking.internal.refund;

import com.sumit.movieticketbookingsystem.shared.persistence.AuditedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;

import java.util.ArrayList;
import java.util.List;

/**
 * The cancellation terms an admin sets up. Bookings copy them at confirmation (RefundPolicySnapshot),
 * so editing a policy only affects bookings confirmed afterwards.
 */
@Entity
public class RefundPolicy extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private RefundPolicyType type;

    private boolean refundFees;

    @Column(name = "is_default")
    private boolean defaultPolicy;

    @ElementCollection(fetch = FetchType.EAGER)                // a handful of rows, and always needed
    @CollectionTable(name = "refund_policy_slab", joinColumns = @JoinColumn(name = "policy_id"))
    @OrderBy("minHoursBefore DESC")
    private List<RefundSlab> slabs = new ArrayList<>();

    protected RefundPolicy() {
    }

    public RefundPolicy(Terms terms) {
        update(terms);
    }

    public void update(Terms terms) {
        this.name = terms.name();
        this.type = terms.type();
        this.refundFees = terms.refundFees();
        this.slabs.clear();
        this.slabs.addAll(terms.slabs());
    }

    void makeDefault(boolean isDefault) {
        this.defaultPolicy = isDefault;
    }

    public RefundPolicySnapshot snapshot() {
        return new RefundPolicySnapshot(id, name, type, refundFees, List.copyOf(slabs));
    }

    // slabs are only used by SLAB policies and stay empty otherwise
    public record Terms(String name, RefundPolicyType type, boolean refundFees, List<RefundSlab> slabs) {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RefundPolicyType getType() {
        return type;
    }

    public boolean isRefundFees() {
        return refundFees;
    }

    public boolean isDefaultPolicy() {
        return defaultPolicy;
    }

    public List<RefundSlab> getSlabs() {
        return slabs;
    }
}
