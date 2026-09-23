package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.InitiatePayment;
import com.sumit.movieticketbookingsystem.payment.PaymentApi;
import com.sumit.movieticketbookingsystem.payment.PaymentDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentResult;
import com.sumit.movieticketbookingsystem.payment.PaymentSucceeded;
import com.sumit.movieticketbookingsystem.payment.PaymentSummary;
import com.sumit.movieticketbookingsystem.payment.RefundReason;
import com.sumit.movieticketbookingsystem.payment.RefundRequest;
import com.sumit.movieticketbookingsystem.payment.SimulatedOutcome;
import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
class PaymentFacade implements PaymentApi {

    private static final Logger log = LoggerFactory.getLogger(PaymentFacade.class);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentProcessorRegistry processors;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Duration simulatedDelay;
    private final Clock clock;

    PaymentFacade(PaymentRepository payments, RefundRepository refunds, PaymentProcessorRegistry processors,
            ApplicationEventPublisher events, TransactionTemplate tx, BookingProperties properties, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.processors = processors;
        this.events = events;
        this.tx = tx;
        this.simulatedDelay = properties.payment().simulatedDelay();
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

        return switch (result.outcome()) {
            case SUCCEEDED -> tx.execute(status -> {
                payments.findById(paymentId).orElseThrow().succeed(result.reference(), Instant.now(clock));
                return new PaymentResult(paymentId, PaymentResult.Status.SUCCESS, result.reference(), null);
            });
            case FAILED -> tx.execute(status -> {
                payments.findById(paymentId).orElseThrow().fail(result.failureReason(), Instant.now(clock));
                return new PaymentResult(paymentId, PaymentResult.Status.FAILED, null, result.failureReason());
            });
            case PENDING -> {
                completeLater(paymentId);
                yield new PaymentResult(paymentId, PaymentResult.Status.PENDING, null, null);
            }
        };
    }

    @Override
    @Transactional
    public UUID requestRefund(RefundRequest request) {
        Payment payment = payments.findByBookingIdAndStatus(request.bookingId(), PaymentStatus.SUCCESS)
                .orElseThrow(() -> new IllegalStateException("Booking " + request.bookingId() + " has no payment"));
        if (request.reason() == RefundReason.LATE_PAYMENT) {
            Optional<Refund> existing = refunds.findByPaymentIdAndReason(payment.getId(), RefundReason.LATE_PAYMENT);
            if (existing.isPresent()) {
                return existing.get().getId();       // a redelivered event asked twice
            }
        }
        if (payments.addRefunded(payment.getId(), request.amountPaise()) == 0) {
            throw new IllegalStateException("Refunding " + request.amountPaise() + " would exceed what booking "
                    + request.bookingId() + " paid");
        }
        Refund refund = refunds.save(new Refund(UUID.randomUUID(), payment.getId(), request.bookingId(),
                request.cancellationId(), request.amountPaise(), request.reason(), Instant.now(clock)));
        events.publishEvent(new RefundRequested(refund.getId()));
        return refund.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentSummary> summary(UUID bookingId) {
        return payments.findByBookingIdAndStatus(bookingId, PaymentStatus.SUCCESS)
                .map(payment -> new PaymentSummary(payment.getId(), payment.getMethod(), payment.getMaskedDetails(),
                        payment.getAmountPaise(),
                        refunds.findByPaymentIdOrderByCreatedAt(payment.getId()).stream()
                                .map(refund -> new PaymentSummary.Refund(refund.getId(), refund.getCancellationId(),
                                        refund.getAmountPaise(), refund.getReason(),
                                        PaymentSummary.Refund.Status.valueOf(refund.getStatus().name())))
                                .toList()));
    }

    // ponytail: the simulated gateway's later answer lives only in memory, so a restart loses it and the booking
    // stays PAYMENT_PENDING until the sweeper expires it. A real gateway would call back through a webhook.
    private void completeLater(UUID paymentId) {
        CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
                    Payment payment = payments.findById(paymentId).orElseThrow();
                    payment.succeed(PaymentSimulator.reference("SIM"), Instant.now(clock));
                    events.publishEvent(new PaymentSucceeded(paymentId, payment.getBookingId()));
                }), CompletableFuture.delayedExecutor(simulatedDelay.toMillis(), TimeUnit.MILLISECONDS))
                .exceptionally(error -> {
                    log.error("Completing delayed payment {} failed", paymentId, error);
                    return null;
                });
    }
}
