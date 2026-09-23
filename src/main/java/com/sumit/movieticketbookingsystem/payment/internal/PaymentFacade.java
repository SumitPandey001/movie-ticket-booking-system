package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.InitiatePayment;
import com.sumit.movieticketbookingsystem.payment.PaymentApi;
import com.sumit.movieticketbookingsystem.payment.PaymentDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentResult;
import com.sumit.movieticketbookingsystem.payment.SimulatedOutcome;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
class PaymentFacade implements PaymentApi {

    private final PaymentRepository payments;
    private final PaymentProcessorRegistry processors;
    private final TransactionTemplate tx;
    private final Clock clock;

    PaymentFacade(PaymentRepository payments, PaymentProcessorRegistry processors, TransactionTemplate tx,
            Clock clock) {
        this.payments = payments;
        this.processors = processors;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID initiate(InitiatePayment request) {
        PaymentProcessor processor = processors.forMethod(request.details().method());
        processor.validate(request.details());
        Payment payment = new Payment(UUID.randomUUID(), request.bookingId(), request.customerId(), request.reference(),
                request.details().method(), request.amountPaise(), processor.mask(request.details()),
                Instant.now(clock));
        return payments.save(payment).getId();
    }

    // Deliberately no transaction around the gateway call: the database is only touched before and after it.
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public PaymentResult execute(UUID paymentId, PaymentDetails details, SimulatedOutcome outcome) {
        Payment payment = payments.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment", paymentId));
        if (details.method() != payment.getMethod()) {
            throw new ValidationException("Payment " + paymentId + " was started as " + payment.getMethod());
        }
        ProcessorResult result = processors.forMethod(payment.getMethod()).charge(payment, outcome);

        return tx.execute(status -> {
            Payment current = payments.findById(paymentId).orElseThrow();
            if (result.succeeded()) {
                current.succeed(result.providerTxnId(), Instant.now(clock));
                return new PaymentResult(paymentId, PaymentResult.Status.SUCCESS, result.providerTxnId(), null);
            }
            current.fail(result.failureReason(), Instant.now(clock));
            return new PaymentResult(paymentId, PaymentResult.Status.FAILED, null, result.failureReason());
        });
    }
}
