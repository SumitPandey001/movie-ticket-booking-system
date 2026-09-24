package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.booking.internal.domain.Cancellation;
import com.sumit.movieticketbookingsystem.booking.internal.service.CancellationService.CancellationResult;

import java.util.UUID;

// refundId is null when nothing is refunded: a non-refundable ticket, or too close to the show
record CancellationResponse(UUID cancellationId, int refundPercent, long refundPaise, UUID refundId,
                            BookingResponse booking) {

    static CancellationResponse from(CancellationResult result) {
        Cancellation cancellation = result.cancellation();
        return new CancellationResponse(cancellation.getId(), cancellation.getRefundPercent(),
                cancellation.getRefundPaise(), result.refundId(), BookingResponse.from(result.booking()));
    }
}
