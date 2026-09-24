package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The processor for each payment method. Startup fails if a method has no processor or two.
 */
@Component
class PaymentProcessorRegistry {

    private final Map<PaymentMethod, PaymentProcessor> byMethod = new EnumMap<>(PaymentMethod.class);

    PaymentProcessorRegistry(List<PaymentProcessor> processors) {
        for (PaymentProcessor processor : processors) {
            if (byMethod.put(processor.method(), processor) != null) {
                throw new IllegalStateException("Two processors for " + processor.method());
            }
        }
        Set<PaymentMethod> missing = EnumSet.allOf(PaymentMethod.class);
        missing.removeAll(byMethod.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No processor for " + missing);
        }
    }

    PaymentProcessor forMethod(PaymentMethod method) {
        return byMethod.get(method);
    }
}
