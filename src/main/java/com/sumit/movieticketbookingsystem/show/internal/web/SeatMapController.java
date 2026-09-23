package com.sumit.movieticketbookingsystem.show.internal.web;

import com.sumit.movieticketbookingsystem.show.internal.service.SeatMapService;
import com.sumit.movieticketbookingsystem.show.internal.service.SeatMapService.SeatMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SeatMapController {

    private final SeatMapService seatMapService;

    SeatMapController(SeatMapService seatMapService) {
        this.seatMapService = seatMapService;
    }

    @GetMapping("/api/v1/shows/{showId}/seats")
    SeatMap seatMap(@PathVariable long showId) {
        return seatMapService.seatMap(showId);
    }
}
