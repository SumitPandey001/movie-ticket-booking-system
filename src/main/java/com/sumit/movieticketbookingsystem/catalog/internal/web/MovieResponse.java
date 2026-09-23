package com.sumit.movieticketbookingsystem.catalog.internal.web;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.Certification;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.Movie;

import java.time.LocalDate;

record MovieResponse(long id, String title, int durationMin, Certification certification, LocalDate releaseDate,
                     boolean active) {

    static MovieResponse from(Movie movie) {
        return new MovieResponse(movie.getId(), movie.getTitle(), movie.getDurationMinutes(),
                movie.getCertification(), movie.getReleaseDate(), movie.isActive());
    }
}
