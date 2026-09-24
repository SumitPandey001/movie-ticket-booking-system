package com.sumit.movieticketbookingsystem.payment;

import java.util.UUID;

// reference is the booking reference, shown on the customer's statement
public record InitiatePayment(UUID bookingId, UUID customerId, String reference, long amountPaise,
                              PaymentDetails details) {
}
