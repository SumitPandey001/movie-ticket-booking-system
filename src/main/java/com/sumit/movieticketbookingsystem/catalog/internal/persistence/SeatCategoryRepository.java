package com.sumit.movieticketbookingsystem.catalog.internal.persistence;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.SeatCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatCategoryRepository extends JpaRepository<SeatCategory, Long> {
}
