package com.sumit.movieticketbookingsystem.catalog.internal.persistence;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.Theater;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TheaterRepository extends JpaRepository<Theater, Long> {

    // The theater is always loaded with its screens, since screen changes go through it.
    @Override
    @EntityGraph(attributePaths = "screens")
    Optional<Theater> findById(Long id);

    @Query("select t from Theater t join fetch t.screens where t.id = "
            + "(select s.theater.id from Screen s where s.id = :screenId)")
    Optional<Theater> findByScreenId(long screenId);

    boolean existsByCityIdAndNameIgnoreCase(Long cityId, String name);

    boolean existsByCityIdAndNameIgnoreCaseAndIdNot(Long cityId, String name, Long id);
}
