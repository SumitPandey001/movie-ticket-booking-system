package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicy;
import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicyType;
import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundSlab;

import java.util.List;

record RefundPolicyResponse(long id, String name, RefundPolicyType type, boolean refundFees,
                            boolean isDefault, List<RefundSlab> slabs) {

    static RefundPolicyResponse from(RefundPolicy policy) {
        return new RefundPolicyResponse(policy.getId(), policy.getName(), policy.getType(),
                policy.isRefundFees(), policy.isDefaultPolicy(), List.copyOf(policy.getSlabs()));
    }
}
