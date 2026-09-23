package com.sumit.movieticketbookingsystem.pricing.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface DayPricingRuleRepository extends JpaRepository<DayPricingRule, Long> {

    List<DayPricingRule> findAllByOrderById();
}
