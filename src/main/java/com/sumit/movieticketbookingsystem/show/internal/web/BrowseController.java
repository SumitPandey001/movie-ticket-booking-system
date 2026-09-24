package com.sumit.movieticketbookingsystem.show.internal.web;

import com.sumit.movieticketbookingsystem.show.internal.domain.TimeSlot;
import com.sumit.movieticketbookingsystem.show.internal.service.BrowseService;
import com.sumit.movieticketbookingsystem.show.internal.service.BrowseService.Movie;
import com.sumit.movieticketbookingsystem.show.internal.service.BrowseService.Showtimes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
class BrowseController {

    private final BrowseService browseService;

    BrowseController(BrowseService browseService) {
        this.browseService = browseService;
    }

    @GetMapping("/cities/{cityId}/movies")
    List<Movie> moviesShowing(@PathVariable long cityId) {
        return browseService.moviesShowing(cityId);
    }

    @GetMapping("/movies/{movieId}/dates")
    List<LocalDate> dates(@PathVariable long movieId, @RequestParam long cityId) {
        return browseService.dates(movieId, cityId);
    }

    /** slot takes several values, e.g. slot=EVENING,NIGHT. */
    @GetMapping("/movies/{movieId}/shows")
    Showtimes showtimes(@PathVariable long movieId, @RequestParam long cityId, @RequestParam LocalDate date,
            @RequestParam(defaultValue = "") Set<TimeSlot> slot,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String format) {
        return browseService.showtimes(movieId, cityId, date, slot, language, format);
    }
}
