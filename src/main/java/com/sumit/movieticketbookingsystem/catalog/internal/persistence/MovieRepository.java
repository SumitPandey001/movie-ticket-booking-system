package com.sumit.movieticketbookingsystem.catalog.internal.persistence;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {
}
