package com.sumit.movieticketbookingsystem.payment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * What the customer entered to pay, read from JSON by its {@code type}, e.g.
 * {@code {"type": "UPI", "vpa": "asha@okbank"}}. Never stored; only a masked form is kept on the payment.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PaymentDetails.CardDetails.class, name = "CARD"),
        @JsonSubTypes.Type(value = PaymentDetails.UpiDetails.class, name = "UPI"),
        @JsonSubTypes.Type(value = PaymentDetails.NetBankingDetails.class, name = "NET_BANKING"),
        @JsonSubTypes.Type(value = PaymentDetails.WalletDetails.class, name = "WALLET")})
public sealed interface PaymentDetails {

    PaymentMethod method();

    record CardDetails(String number, int expiryMonth, int expiryYear, String cvv, String holderName)
            implements PaymentDetails {

        @Override
        public PaymentMethod method() {
            return PaymentMethod.CARD;
        }

        // never let card data reach a log
        @Override
        public String toString() {
            return "CardDetails[redacted]";
        }
    }

    record UpiDetails(String vpa) implements PaymentDetails {

        @Override
        public PaymentMethod method() {
            return PaymentMethod.UPI;
        }
    }

    record NetBankingDetails(String bankCode) implements PaymentDetails {

        @Override
        public PaymentMethod method() {
            return PaymentMethod.NET_BANKING;
        }
    }

    record WalletDetails(String provider) implements PaymentDetails {

        @Override
        public PaymentMethod method() {
            return PaymentMethod.WALLET;
        }
    }
}
