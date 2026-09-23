package com.sumit.movieticketbookingsystem.booking.internal.refund;

import java.util.List;

/**
 * A booking's own copy of its refund policy, stored as JSON on the booking. Slabs are ordered from the most
 * hours before the show to the fewest.
 */
public record RefundPolicySnapshot(long policyId, String name, RefundPolicyType type, boolean refundFees,
                                   List<RefundSlab> slabs) {
}
