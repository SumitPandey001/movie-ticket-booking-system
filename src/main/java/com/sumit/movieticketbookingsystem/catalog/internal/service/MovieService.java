package com.sumit.movieticketbookingsystem.catalog.internal.service;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.Certification;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.Movie;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.MovieRepository;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// Titles aren't unique on purpose: remakes and re-releases share them.
@Service
public class MovieService {

    private final MovieRepository movies;

    public MovieService(MovieRepository movies) {
        this.movies = movies;
    }

    @Transactional(readOnly = true)
    public Movie movie(long id) {
        return find(id);
    }

    @Transactional
    public Movie create(String title, int durationMinutes, Certification certification, LocalDate releaseDate) {
        return movies.save(new Movie(title.strip(), durationMinutes, certification, releaseDate));
    }

    @Transactional
    public Movie update(long id, String title, int durationMinutes, Certification certification,
            LocalDate releaseDate) {
        Movie movie = find(id);
        movie.update(title.strip(), durationMinutes, certification, releaseDate);
        return movie;
    }

    @Transactional
    public void deactivate(long id) {
        find(id).deactivate();
    }

    private Movie find(long id) {
        return movies.findById(id).orElseThrow(() -> new NotFoundException("Movie", id));
    }
}
