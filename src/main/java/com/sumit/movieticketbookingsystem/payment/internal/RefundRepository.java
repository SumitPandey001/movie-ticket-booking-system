package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.RefundReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByPaymentIdAndReason(UUID paymentId, RefundReason reason);

    List<Refund> findByPaymentIdOrderByCreatedAt(UUID paymentId);
}
