package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Override
    @EntityGraph(attributePaths = "scopes")
    Optional<Coupon> findById(Long id);

    @EntityGraph(attributePaths = "scopes")
    List<Coupon> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = "scopes")
    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);
}
