package com.sumit.movieticketbookingsystem.catalog.internal.web;

import com.sumit.movieticketbookingsystem.catalog.internal.service.CityService;
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
@RequestMapping("/api/v1/admin/cities")
class CityAdminController {

    private final CityService cityService;

    CityAdminController(CityService cityService) {
        this.cityService = cityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CityResponse create(@Valid @RequestBody CityRequest request) {
        return CityResponse.from(cityService.create(request.name(), request.state(), request.timezone()));
    }

    @PutMapping("/{id}")
    CityResponse update(@PathVariable long id, @Valid @RequestBody CityRequest request) {
        return CityResponse.from(cityService.update(id, request.name(), request.state(), request.timezone()));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        cityService.deactivate(id);
    }
}
