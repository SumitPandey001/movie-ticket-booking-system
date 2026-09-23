package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.sumit.movieticketbookingsystem.booking.CancellationReason;
import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.booking.internal.service.BookingQueryService.BookingDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentMethod;
import com.sumit.movieticketbookingsystem.payment.PaymentSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The same fields as {@link BookingResponse}, plus the show, cancellations and payment. */
record BookingDetailsResponse(@JsonUnwrapped BookingResponse booking, String movieTitle, String theaterName,
                              List<CancellationView> cancellations, PaymentView payment) {

    static BookingDetailsResponse from(BookingDetails details) {
        Booking booking = details.booking();
        return new BookingDetailsResponse(BookingResponse.from(booking), details.show().movieTitle(),
                details.show().theaterName(),
                details.cancellations().stream().map(cancellation -> new CancellationView(cancellation.getId(),
                        cancellation.getReason(), seatsOf(booking, cancellation.getId()),
                        cancellation.getRefundPercent(), cancellation.getRefundPaise(),
                        cancellation.getCreatedAt())).toList(),
                details.payment() == null ? null : PaymentView.from(details.payment()));
    }

    private static List<String> seatsOf(Booking booking, UUID cancellationId) {
        return booking.getSeats().stream()
                .filter(seat -> cancellationId.equals(seat.cancellationId()))
                .map(BookingSeat::seatLabel)
                .toList();
    }

    record CancellationView(UUID cancellationId, CancellationReason reason, List<String> seats, int refundPercent,
                            long refundPaise, Instant cancelledAt) {
    }

    /** @param refunds each with its cancellationId (null for a late-payment refund) and where it stands */
    record PaymentView(UUID paymentId, PaymentMethod method, String paidWith, long amountPaise,
                       List<PaymentSummary.Refund> refunds) {

        static PaymentView from(PaymentSummary summary) {
            return new PaymentView(summary.paymentId(), summary.method(), summary.maskedDetails(),
                    summary.amountPaise(), summary.refunds());
        }
    }
}
