package com.sumit.movieticketbookingsystem.payment.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByBookingIdAndStatus(UUID bookingId, PaymentStatus status);

    /** Records a refund against the payment unless it would exceed what was paid; returns 0 rows if it would. */
    @Modifying
    @Query(value = """
            UPDATE payment SET refunded_paise = refunded_paise + :amount, version = version + 1
            WHERE id = :paymentId AND refunded_paise + :amount <= amount_paise
            """, nativeQuery = true)
    int addRefunded(UUID paymentId, long amount);
}
