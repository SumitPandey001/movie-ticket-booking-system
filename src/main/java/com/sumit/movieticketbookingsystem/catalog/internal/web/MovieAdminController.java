package com.sumit.movieticketbookingsystem.catalog.internal.web;

import com.sumit.movieticketbookingsystem.catalog.internal.service.MovieService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/movies")
class MovieAdminController {

    private final MovieService movieService;

    MovieAdminController(MovieService movieService) {
        this.movieService = movieService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MovieResponse create(@Valid @RequestBody MovieRequest request) {
        return MovieResponse.from(movieService.create(
                request.title(), request.durationMin(), request.certification(), request.releaseDate()));
    }

    @PutMapping("/{id}")
    MovieResponse update(@PathVariable long id, @Valid @RequestBody MovieRequest request) {
        return MovieResponse.from(movieService.update(
                id, request.title(), request.durationMin(), request.certification(), request.releaseDate()));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        movieService.deactivate(id);
    }
}
