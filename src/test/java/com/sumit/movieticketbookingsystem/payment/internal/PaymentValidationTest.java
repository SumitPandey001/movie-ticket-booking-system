package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.PaymentDetails.CardDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentDetails.NetBankingDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentDetails.UpiDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentDetails.WalletDetails;
import com.sumit.movieticketbookingsystem.shared.TestBookingProperties;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentValidationTest {

    private static final Clock OCT_2026 = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);
    private static final String VISA_TEST = "4242 4242 4242 4242";

    private final PaymentSimulator simulator = new PaymentSimulator();
    private final CardPaymentProcessor cards = new CardPaymentProcessor(simulator, OCT_2026);
    private final UpiPaymentProcessor upi = new UpiPaymentProcessor(simulator);
    private final NetBankingPaymentProcessor netBanking =
            new NetBankingPaymentProcessor(simulator, TestBookingProperties.defaults());
    private final WalletPaymentProcessor wallets =
            new WalletPaymentProcessor(simulator, TestBookingProperties.defaults());

    @Test
    void validCardPassesAndOnlyTheLastFourDigitsAreKept() {
        CardDetails card = new CardDetails(VISA_TEST, 12, 2028, "123", "Asha Rao");

        assertThatCode(() -> cards.validate(card)).doesNotThrowAnyException();
        assertThat(cards.mask(card)).isEqualTo("•••• 4242");
        assertThat(card.toString()).isEqualTo("CardDetails[redacted]").doesNotContain("4242");
    }

    @Test
    void cardChecks() {
        assertInvalid(new CardDetails("4242 4242 4242 4241", 12, 2028, "123", "A"), "The card number isn't valid");
        assertInvalid(new CardDetails("1234", 12, 2028, "123", "A"), "The card number isn't valid");
        assertInvalid(new CardDetails(VISA_TEST, 12, 2028, "12", "A"), "The CVV must be 3 or 4 digits");
        assertInvalid(new CardDetails(VISA_TEST, 13, 2028, "123", "A"), "The card's expiry date isn't valid");
        assertInvalid(new CardDetails(VISA_TEST, 9, 2026, "123", "A"), "The card has expired");
        assertThatCode(() -> cards.validate(new CardDetails(VISA_TEST, 10, 2026, "123", "A")))
                .doesNotThrowAnyException();                                   // valid until the end of its month
    }

    @Test
    void upiIds() {
        assertThatCode(() -> upi.validate(new UpiDetails("asha.rao@okbank"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> upi.validate(new UpiDetails("asha.rao"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> upi.validate(new UpiDetails("a@1bank"))).isInstanceOf(ValidationException.class);
        assertThat(upi.mask(new UpiDetails("asha@okbank"))).isEqualTo("asha@okbank");
    }

    @Test
    void banksAndWalletsMustBeOnTheList() {
        assertThatCode(() -> netBanking.validate(new NetBankingDetails("HDFC"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> netBanking.validate(new NetBankingDetails("XYZ")))
                .isInstanceOf(ValidationException.class);
        assertThatCode(() -> wallets.validate(new WalletDetails("PAYTM"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> wallets.validate(new WalletDetails("APPLEPAY")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void luhn() {
        assertThat(CardPaymentProcessor.passesLuhn("4242424242424242")).isTrue();
        assertThat(CardPaymentProcessor.passesLuhn("5555555555554444")).isTrue();
        assertThat(CardPaymentProcessor.passesLuhn("4242424242424241")).isFalse();
    }

    private void assertInvalid(CardDetails card, String message) {
        assertThatThrownBy(() -> cards.validate(card)).isInstanceOf(ValidationException.class).hasMessage(message);
    }
}
