package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.payment.PaymentDetails;
import com.sumit.movieticketbookingsystem.payment.SimulatedOutcome;
import jakarta.validation.constraints.NotNull;

/**
 * e.g. {"details": {"type": "UPI", "vpa": "<upi id>"}, "simulate": "SUCCESS"}.
 * simulate is for demos and defaults to SUCCESS.
 */
record PaymentRequest(@NotNull PaymentDetails details, SimulatedOutcome simulate) {

    SimulatedOutcome outcome() {
        return simulate == null ? SimulatedOutcome.SUCCESS : simulate;
    }
}
