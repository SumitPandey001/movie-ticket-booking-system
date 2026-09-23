package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.RefundCompleted;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Moves the money for a committed refund. Runs after commit, in its own transaction; if the app stops first, the
 * outbox delivers the event again, and a refund that's no longer INITIATED is skipped, so it's never paid twice.
 */
@Component
class RefundExecutor {

    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final PaymentProcessorRegistry processors;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RefundExecutor(RefundRepository refunds, PaymentRepository payments, PaymentProcessorRegistry processors,
            ApplicationEventPublisher events, Clock clock) {
        this.refunds = refunds;
        this.payments = payments;
        this.processors = processors;
        this.events = events;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(RefundRequested requested) {
        Refund refund = refunds.findById(requested.refundId()).orElseThrow();
        if (refund.getStatus() != RefundStatus.INITIATED) {
            return;
        }
        Payment payment = payments.findById(refund.getPaymentId()).orElseThrow();
        ProcessorResult result = processors.forMethod(payment.getMethod()).refund(refund);
        if (result.outcome() != ProcessorResult.Outcome.SUCCEEDED) {
            refund.fail(Instant.now(clock));
            return;
        }
        refund.complete(result.reference(), Instant.now(clock));
        events.publishEvent(new RefundCompleted(refund.getId(), refund.getBookingId(), payment.getCustomerId(),
                payment.getReference(), refund.getAmountPaise(), refund.getReason()));
    }
}
