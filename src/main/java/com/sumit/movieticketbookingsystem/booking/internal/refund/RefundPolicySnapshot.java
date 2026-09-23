package com.sumit.movieticketbookingsystem.booking.internal.refund;

import com.sumit.movieticketbookingsystem.booking.internal.domain.RefundRule;

import java.util.List;

/**
 * A booking's own copy of its refund policy, stored as JSON on the booking. Slabs are ordered from the most
 * hours before the show to the fewest.
 */
public record RefundPolicySnapshot(long policyId, String name, RefundPolicyType type, boolean refundFees,
                                   List<RefundSlab> slabs) {

    /** The one place that maps a kind of policy to its rule; the compiler flags a type without a case. */
    public RefundRule refundRule() {
        return switch (type) {
            case SLAB -> new SlabRefundRule(slabs, refundFees);
            case FULL -> FullRefundRule.INSTANCE;
            case NON_REFUNDABLE -> NoRefundRule.INSTANCE;
        };
    }
}
