package com.sumit.movieticketbookingsystem.catalog.internal.persistence;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.LayoutStatus;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.SeatLayout;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SeatLayoutRepository extends JpaRepository<SeatLayout, Long> {

    @Override
    @EntityGraph(attributePaths = {"seats", "seats.category"})
    Optional<SeatLayout> findById(Long id);

    @EntityGraph(attributePaths = {"seats", "seats.category"})
    List<SeatLayout> findByScreenIdOrderByVersion(long screenId);

    Optional<SeatLayout> findByScreenIdAndStatus(long screenId, LayoutStatus status);

    @Query("select coalesce(max(l.version), 0) from SeatLayout l where l.screenId = :screenId")
    int latestVersion(long screenId);
}
