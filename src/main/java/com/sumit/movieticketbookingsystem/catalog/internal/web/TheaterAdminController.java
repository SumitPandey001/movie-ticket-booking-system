package com.sumit.movieticketbookingsystem.catalog.internal.web;

import com.sumit.movieticketbookingsystem.catalog.internal.service.TheaterAdminService;
import com.sumit.movieticketbookingsystem.catalog.internal.web.TheaterResponse.ScreenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/theaters")
class TheaterAdminController {

    private final TheaterAdminService theaterService;

    TheaterAdminController(TheaterAdminService theaterService) {
        this.theaterService = theaterService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TheaterResponse create(@Valid @RequestBody TheaterRequests.Create request) {
        return TheaterResponse.from(
                theaterService.create(request.cityId(), request.name(), request.area(), request.address()));
    }

    @GetMapping
    List<TheaterResponse> theaters(@RequestParam long cityId) {
        return theaterService.theaters(cityId).stream().map(TheaterResponse::from).toList();
    }

    @GetMapping("/{id}")
    TheaterResponse theater(@PathVariable long id) {
        return TheaterResponse.from(theaterService.theater(id));
    }

    @PutMapping("/{id}")
    TheaterResponse update(@PathVariable long id, @Valid @RequestBody TheaterRequests.Update request) {
        return TheaterResponse.from(theaterService.update(id, request.name(), request.area(), request.address()));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        theaterService.deactivate(id);
    }

    @PostMapping("/{id}/screens")
    @ResponseStatus(HttpStatus.CREATED)
    ScreenResponse addScreen(@PathVariable long id, @Valid @RequestBody TheaterRequests.ScreenName request) {
        return ScreenResponse.from(theaterService.addScreen(id, request.name()));
    }
}
