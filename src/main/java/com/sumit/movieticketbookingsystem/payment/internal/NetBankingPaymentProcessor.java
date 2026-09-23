package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.PaymentDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentDetails.NetBankingDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentMethod;
import com.sumit.movieticketbookingsystem.payment.SimulatedOutcome;
import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class NetBankingPaymentProcessor implements PaymentProcessor {

    private final PaymentSimulator simulator;
    private final List<String> banks;

    NetBankingPaymentProcessor(PaymentSimulator simulator, BookingProperties properties) {
        this.simulator = simulator;
        this.banks = properties.payment().netBankingBanks();
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.NET_BANKING;
    }

    @Override
    public void validate(PaymentDetails details) {
        if (!banks.contains(((NetBankingDetails) details).bankCode())) {
            throw new ValidationException("Net banking is available for " + String.join(", ", banks));
        }
    }

    @Override
    public String mask(PaymentDetails details) {
        return ((NetBankingDetails) details).bankCode();
    }

    @Override
    public ProcessorResult charge(Payment payment, SimulatedOutcome outcome) {
        return simulator.charge(payment, outcome);
    }
}
