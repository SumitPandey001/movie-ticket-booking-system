package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.SimulatedOutcome;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stands in for a real payment gateway; answers whatever the caller asked for. Refunds always go through.
 */
@Component
class PaymentSimulator {

    ProcessorResult charge(Payment payment, SimulatedOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> ProcessorResult.success(reference("SIM"));
            case FAILURE -> ProcessorResult.failure("Declined by issuer (simulated)");
            case DELAYED -> ProcessorResult.pending();
        };
    }

    ProcessorResult refund(Refund refund) {
        return ProcessorResult.success(reference("SIMR"));
    }

    static String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
