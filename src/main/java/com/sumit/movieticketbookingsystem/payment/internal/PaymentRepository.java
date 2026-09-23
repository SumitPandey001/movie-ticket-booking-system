package com.sumit.movieticketbookingsystem.payment.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
