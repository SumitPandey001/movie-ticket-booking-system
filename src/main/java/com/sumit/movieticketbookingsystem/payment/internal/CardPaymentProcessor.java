package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.PaymentDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentDetails.CardDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentMethod;
import com.sumit.movieticketbookingsystem.payment.SimulatedOutcome;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.regex.Pattern;

@Component
class CardPaymentProcessor implements PaymentProcessor {

    private static final Pattern NUMBER = Pattern.compile("\\d{12,19}");
    private static final Pattern CVV = Pattern.compile("\\d{3,4}");

    private final PaymentSimulator simulator;
    private final Clock clock;

    CardPaymentProcessor(PaymentSimulator simulator, Clock clock) {
        this.simulator = simulator;
        this.clock = clock;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CARD;
    }

    @Override
    public void validate(PaymentDetails details) {
        CardDetails card = (CardDetails) details;
        String number = digits(card.number());
        if (!NUMBER.matcher(number).matches() || !passesLuhn(number)) {
            throw new ValidationException("The card number isn't valid");
        }
        if (card.cvv() == null || !CVV.matcher(card.cvv()).matches()) {
            throw new ValidationException("The CVV must be 3 or 4 digits");
        }
        YearMonth expiry;
        try {
            expiry = YearMonth.of(card.expiryYear(), card.expiryMonth());
        } catch (DateTimeException e) {
            throw new ValidationException("The card's expiry date isn't valid");
        }
        if (expiry.isBefore(YearMonth.now(clock))) {
            throw new ValidationException("The card has expired");
        }
    }

    @Override
    public String mask(PaymentDetails details) {
        String number = digits(((CardDetails) details).number());
        return "•••• " + number.substring(number.length() - 4);
    }

    @Override
    public ProcessorResult charge(Payment payment, SimulatedOutcome outcome) {
        return simulator.charge(payment, outcome);
    }

    @Override
    public ProcessorResult refund(Refund refund) {
        return simulator.refund(refund);
    }

    private static String digits(String number) {
        return number == null ? "" : number.replaceAll("[\\s-]", "");
    }

    // every other digit from the right is doubled; the total must end in 0
    static boolean passesLuhn(String number) {
        int sum = 0;
        for (int i = 0; i < number.length(); i++) {
            int digit = number.charAt(number.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }
}
