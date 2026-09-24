package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicy.Terms;
import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicyType;
import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundSlab;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code slabs} only for SLAB, e.g. {@code [{"minHoursBefore": 24, "percent": 100}, {"minHoursBefore": 0,
 * "percent": 0}]}; cancelling with less time left than the smallest slab refunds nothing.
 * {@code refundFees} (SLAB only) also gives back the convenience fee.
 */
record RefundPolicyRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull RefundPolicyType type,
        Boolean refundFees,
        List<@Valid @NotNull SlabRequest> slabs) {

    Terms toTerms() {
        List<RefundSlab> terms = slabs == null ? List.of()
                : slabs.stream().map(slab -> new RefundSlab(slab.minHoursBefore(), slab.percent())).toList();
        return new Terms(name, type, Boolean.TRUE.equals(refundFees), terms);
    }
}
