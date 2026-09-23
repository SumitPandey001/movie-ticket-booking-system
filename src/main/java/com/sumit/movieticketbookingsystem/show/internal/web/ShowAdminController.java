package com.sumit.movieticketbookingsystem.show.internal.web;

import com.sumit.movieticketbookingsystem.show.internal.service.ShowAdminService;
import com.sumit.movieticketbookingsystem.show.internal.service.ShowAdminService.CreateShow;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/shows")
class ShowAdminController {

    private final ShowAdminService showService;

    ShowAdminController(ShowAdminService showService) {
        this.showService = showService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ShowResponse create(@Valid @RequestBody CreateShowRequest request) {
        return ShowResponse.from(showService.create(new CreateShow(request.movieId(), request.screenId(),
                request.startTime().toInstant(), request.language(), request.format(), request.showDate())));
    }

    @GetMapping
    List<ShowResponse> shows(@RequestParam long theaterId, @RequestParam LocalDate date) {
        return showService.shows(theaterId, date).stream().map(ShowResponse::from).toList();
    }

    @PostMapping("/{id}/open")
    ShowResponse open(@PathVariable long id) {
        return ShowResponse.from(showService.open(id));
    }

    @PostMapping("/{id}/cancel")
    ShowResponse cancel(@PathVariable long id) {
        return ShowResponse.from(showService.cancel(id));
    }
}
