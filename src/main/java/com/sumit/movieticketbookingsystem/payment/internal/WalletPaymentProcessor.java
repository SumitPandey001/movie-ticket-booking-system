package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.PaymentDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentDetails.WalletDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentMethod;
import com.sumit.movieticketbookingsystem.payment.SimulatedOutcome;
import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class WalletPaymentProcessor implements PaymentProcessor {

    private final PaymentSimulator simulator;
    private final List<String> wallets;

    WalletPaymentProcessor(PaymentSimulator simulator, BookingProperties properties) {
        this.simulator = simulator;
        this.wallets = properties.payment().wallets();
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.WALLET;
    }

    @Override
    public void validate(PaymentDetails details) {
        if (!wallets.contains(((WalletDetails) details).provider())) {
            throw new ValidationException("Supported wallets are " + String.join(", ", wallets));
        }
    }

    @Override
    public String mask(PaymentDetails details) {
        return ((WalletDetails) details).provider();
    }

    @Override
    public ProcessorResult charge(Payment payment, SimulatedOutcome outcome) {
        return simulator.charge(payment, outcome);
    }

    @Override
    public ProcessorResult refund(Refund refund) {
        return simulator.refund(refund);
    }
}
