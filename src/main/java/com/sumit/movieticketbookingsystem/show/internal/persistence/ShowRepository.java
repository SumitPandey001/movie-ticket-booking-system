package com.sumit.movieticketbookingsystem.show.internal.persistence;

import com.sumit.movieticketbookingsystem.show.internal.domain.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {

    List<Show> findByTheaterIdAndShowDateOrderByStartTime(long theaterId, LocalDate showDate);
}
