package com.sumit.movieticketbookingsystem.booking.internal.refund;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {

    List<RefundPolicy> findAllByOrderByName();

    Optional<RefundPolicy> findByDefaultPolicyTrue();

    boolean existsByNameIgnoreCaseAndIdNot(String name, long id);
}
