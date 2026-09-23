package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.shared.TestBookingProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProcessorRegistryTest {

    private final PaymentSimulator simulator = new PaymentSimulator();
    private final CardPaymentProcessor card = new CardPaymentProcessor(simulator, Clock.systemUTC());
    private final UpiPaymentProcessor upi = new UpiPaymentProcessor(simulator);
    private final NetBankingPaymentProcessor netBanking =
            new NetBankingPaymentProcessor(simulator, TestBookingProperties.defaults());
    private final WalletPaymentProcessor wallet =
            new WalletPaymentProcessor(simulator, TestBookingProperties.defaults());

    @Test
    void acceptsExactlyOneProcessorPerMethod() {
        assertThatCode(() -> new PaymentProcessorRegistry(List.of(card, upi, netBanking, wallet)))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesToStartWithAMethodMissing() {
        assertThatThrownBy(() -> new PaymentProcessorRegistry(List.of(card, upi, netBanking)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No processor for [WALLET]");
    }

    @Test
    void refusesToStartWithTwoProcessorsForOneMethod() {
        assertThatThrownBy(() -> new PaymentProcessorRegistry(List.of(card, upi, upi, netBanking, wallet)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Two processors for UPI");
    }
}
