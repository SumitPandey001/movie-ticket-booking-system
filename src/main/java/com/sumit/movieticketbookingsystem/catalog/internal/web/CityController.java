package com.sumit.movieticketbookingsystem.catalog.internal.web;

import com.sumit.movieticketbookingsystem.catalog.internal.service.CityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cities")
class CityController {

    private final CityService cityService;

    CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping
    List<CityResponse> activeCities() {
        return cityService.activeCities().stream().map(CityResponse::from).toList();
    }
}
