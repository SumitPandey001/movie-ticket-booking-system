package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.PaymentDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentDetails.UpiDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentMethod;
import com.sumit.movieticketbookingsystem.payment.SimulatedOutcome;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
class UpiPaymentProcessor implements PaymentProcessor {

    private static final Pattern VPA = Pattern.compile("[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}");

    private final PaymentSimulator simulator;

    UpiPaymentProcessor(PaymentSimulator simulator) {
        this.simulator = simulator;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.UPI;
    }

    @Override
    public void validate(PaymentDetails details) {
        String vpa = ((UpiDetails) details).vpa();
        if (vpa == null || !VPA.matcher(vpa).matches()) {
            throw new ValidationException("That isn't a valid UPI ID");
        }
    }

    @Override
    public String mask(PaymentDetails details) {
        return ((UpiDetails) details).vpa();
    }

    @Override
    public ProcessorResult charge(Payment payment, SimulatedOutcome outcome) {
        return simulator.charge(payment, outcome);
    }
}
