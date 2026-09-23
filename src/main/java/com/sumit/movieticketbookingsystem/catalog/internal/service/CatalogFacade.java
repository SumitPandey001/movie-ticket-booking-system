package com.sumit.movieticketbookingsystem.catalog.internal.service;

import com.sumit.movieticketbookingsystem.catalog.CatalogApi;
import com.sumit.movieticketbookingsystem.catalog.CitySummary;
import com.sumit.movieticketbookingsystem.catalog.LayoutView;
import com.sumit.movieticketbookingsystem.catalog.MovieInfo;
import com.sumit.movieticketbookingsystem.catalog.ScreenInfo;
import com.sumit.movieticketbookingsystem.catalog.SeatCategoryInfo;
import com.sumit.movieticketbookingsystem.catalog.TheaterSummary;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.City;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.LayoutStatus;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.Movie;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.Screen;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.SeatLayout;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.Theater;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.CityRepository;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.MovieRepository;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.SeatCategoryRepository;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.SeatLayoutRepository;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.TheaterRepository;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
class CatalogFacade implements CatalogApi {

    private final TheaterRepository theaters;
    private final CityRepository cities;
    private final SeatLayoutRepository layouts;
    private final MovieRepository movies;
    private final SeatCategoryRepository categories;

    CatalogFacade(TheaterRepository theaters, CityRepository cities, SeatLayoutRepository layouts,
            MovieRepository movies, SeatCategoryRepository categories) {
        this.theaters = theaters;
        this.cities = cities;
        this.layouts = layouts;
        this.movies = movies;
        this.categories = categories;
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
        List<LayoutView.Seat> seats = layout.getSeats().stream()
                .map(seat -> new LayoutView.Seat(seat.getId(), seat.getLabel(), seat.getCategory().getId(),
                        seat.getGridRow(), seat.getGridCol(), seat.getSeatType().name()))
                .toList();
        return new LayoutView(layout.getId(), layout.getScreenId(), layout.getGridRows(), layout.getGridCols(),
                layout.getTotalSeats(), seats);
    }

    @Override
    public CitySummary city(long cityId) {
        City city = findCity(cityId);
        return new CitySummary(city.getId(), city.getName(), city.getTimezone(), city.isActive());
    }

    @Override
    public MovieInfo movie(long movieId) {
        return movieInfo(movies.findById(movieId).orElseThrow(() -> new NotFoundException("Movie", movieId)));
    }

    @Override
    public Map<Long, MovieInfo> movies(Collection<Long> movieIds) {
        return movies.findAllById(movieIds).stream()
                .map(CatalogFacade::movieInfo)
                .collect(Collectors.toMap(MovieInfo::movieId, Function.identity()));
    }

    @Override
    public Map<Long, TheaterSummary> theaters(Collection<Long> theaterIds) {
        return theaters.findAllById(theaterIds).stream()
                .map(theater -> new TheaterSummary(theater.getId(), theater.getName(), theater.getArea()))
                .collect(Collectors.toMap(TheaterSummary::theaterId, Function.identity()));
    }

    @Override
    public List<SeatCategoryInfo> seatCategories() {
        return categories.findAll(Sort.by("sortOrder")).stream()
                .map(category -> new SeatCategoryInfo(category.getId(), category.getCode(), category.getName()))
                .toList();
    }

    private static MovieInfo movieInfo(Movie movie) {
        return new MovieInfo(movie.getId(), movie.getTitle(), Duration.ofMinutes(movie.getDurationMinutes()),
                movie.getCertification().name(), movie.isActive());
    }

    private City findCity(long cityId) {
        return cities.findById(cityId).orElseThrow(() -> new NotFoundException("City", cityId));
    }
}
