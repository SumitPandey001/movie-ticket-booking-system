package com.sumit.movieticketbookingsystem.show.internal.service;

import com.sumit.movieticketbookingsystem.catalog.CatalogApi;
import com.sumit.movieticketbookingsystem.catalog.CitySummary;
import com.sumit.movieticketbookingsystem.catalog.MovieInfo;
import com.sumit.movieticketbookingsystem.catalog.ScreenInfo;
import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import com.sumit.movieticketbookingsystem.shared.persistence.ConstraintViolations;
import com.sumit.movieticketbookingsystem.show.internal.domain.Show;
import com.sumit.movieticketbookingsystem.show.internal.persistence.ShowRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class ShowAdminService {

    private final ShowRepository shows;
    private final CatalogApi catalog;
    private final ShowDateResolver showDateResolver;
    private final Duration cleaningBuffer;
    private final Clock clock;

    ShowAdminService(ShowRepository shows, CatalogApi catalog, ShowDateResolver showDateResolver,
            BookingProperties properties, Clock clock) {
        this.shows = shows;
        this.catalog = catalog;
        this.showDateResolver = showDateResolver;
        this.cleaningBuffer = properties.cleaningBuffer();
        this.clock = clock;
    }

    /**
     * @param showDate optional; defaults to the late-night rule in {@link ShowDateResolver}
     */
    public record CreateShow(long movieId, long screenId, Instant start, String language, String format,
                             LocalDate showDate) {
    }

    @Transactional
    public Show create(CreateShow command) {
        ScreenInfo screen = catalog.screen(command.screenId());
        if (!screen.active()) {
            throw new ValidationException("Screen " + screen.screenId() + " or its theater or city is inactive");
        }
        if (screen.activeLayoutId() == null) {
            throw new ValidationException("Screen " + screen.screenId() + " has no active seat layout");
        }
        MovieInfo movie = catalog.movie(command.movieId());
        if (!movie.active()) {
            throw new ValidationException("Movie " + movie.movieId() + " is inactive");
        }
        if (!command.start().isAfter(Instant.now(clock))) {
            throw new ValidationException("Show must start in the future");
        }

        CitySummary city = catalog.city(screen.cityId());
        Instant end = command.start().plus(movie.duration());
        Show.Timing timing = new Show.Timing(
                showDateResolver.showDate(command.start(), city.zone(), command.showDate()),
                command.start(), end, end.plus(cleaningBuffer));
        Show.Placement placement = new Show.Placement(
                screen.screenId(), screen.theaterId(), screen.cityId(), screen.activeLayoutId());
        int totalSeats = catalog.layout(screen.activeLayoutId()).totalSeats();

        Show show = new Show(placement, movie.movieId(), timing, normalize(command.language()),
                normalize(command.format()), totalSeats);
        try {
            return shows.saveAndFlush(show);
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.isViolationOf(e, "show_no_overlap")) {
                throw new ShowOverlapException(screen.screenId());
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<Show> shows(long theaterId, LocalDate date) {
        return shows.findByTheaterIdAndShowDateOrderByStartTime(theaterId, date);
    }

    @Transactional
    public Show open(long showId) {
        Show show = find(showId);
        show.open();
        return show;
    }

    @Transactional
    public Show cancel(long showId) {
        Show show = find(showId);
        show.cancel();
        return show;
    }

    private Show find(long showId) {
        return shows.findById(showId).orElseThrow(() -> new NotFoundException("Show", showId));
    }

    private static String normalize(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }
}
