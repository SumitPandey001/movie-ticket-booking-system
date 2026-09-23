package com.sumit.movieticketbookingsystem.catalog.internal.service;

import com.sumit.movieticketbookingsystem.catalog.CatalogApi;
import com.sumit.movieticketbookingsystem.catalog.CitySummary;
import com.sumit.movieticketbookingsystem.catalog.LayoutView;
import com.sumit.movieticketbookingsystem.catalog.MovieInfo;
import com.sumit.movieticketbookingsystem.catalog.ScreenInfo;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.City;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.LayoutStatus;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.Movie;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.Screen;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.SeatLayout;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.Theater;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.CityRepository;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.MovieRepository;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.SeatLayoutRepository;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.TheaterRepository;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@Transactional(readOnly = true)
class CatalogFacade implements CatalogApi {

    private final TheaterRepository theaters;
    private final CityRepository cities;
    private final SeatLayoutRepository layouts;
    private final MovieRepository movies;

    CatalogFacade(TheaterRepository theaters, CityRepository cities, SeatLayoutRepository layouts,
            MovieRepository movies) {
        this.theaters = theaters;
        this.cities = cities;
        this.layouts = layouts;
        this.movies = movies;
    }

    @Override
    public ScreenInfo screen(long screenId) {
        Theater theater = theaters.findByScreenId(screenId).orElseThrow(() -> new NotFoundException("Screen", screenId));
        Screen screen = theater.screen(screenId);
        boolean active = screen.isActive() && theater.isActive() && findCity(theater.getCityId()).isActive();
        Long activeLayoutId = layouts.findByScreenIdAndStatus(screenId, LayoutStatus.ACTIVE)
                .map(SeatLayout::getId)
                .orElse(null);
        return new ScreenInfo(screenId, screen.getName(), theater.getId(), theater.getCityId(), active, activeLayoutId);
    }

    @Override
    public LayoutView layout(long layoutId) {
        SeatLayout layout = layouts.findById(layoutId).orElseThrow(() -> new NotFoundException("Seat layout", layoutId));
        return new LayoutView(layout.getId(), layout.getScreenId(), layout.getTotalSeats());
    }

    @Override
    public CitySummary city(long cityId) {
        City city = findCity(cityId);
        return new CitySummary(city.getId(), city.getName(), city.getTimezone(), city.isActive());
    }

    @Override
    public MovieInfo movie(long movieId) {
        Movie movie = movies.findById(movieId).orElseThrow(() -> new NotFoundException("Movie", movieId));
        return new MovieInfo(movie.getId(), movie.getTitle(), Duration.ofMinutes(movie.getDurationMinutes()),
                movie.getCertification().name(), movie.isActive());
    }

    private City findCity(long cityId) {
        return cities.findById(cityId).orElseThrow(() -> new NotFoundException("City", cityId));
    }
}
