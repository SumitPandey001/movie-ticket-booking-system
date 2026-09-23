package com.sumit.movieticketbookingsystem.catalog.internal.persistence;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findByActiveTrueOrderByName();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
