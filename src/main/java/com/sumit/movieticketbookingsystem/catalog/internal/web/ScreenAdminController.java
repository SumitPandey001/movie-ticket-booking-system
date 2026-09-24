package com.sumit.movieticketbookingsystem.catalog.internal.web;

import com.sumit.movieticketbookingsystem.catalog.internal.service.TheaterAdminService;
import com.sumit.movieticketbookingsystem.catalog.internal.web.TheaterResponse.ScreenResponse;
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
@RequestMapping("/api/v1/admin/screens")
class ScreenAdminController {

    private final TheaterAdminService theaterService;

    ScreenAdminController(TheaterAdminService theaterService) {
        this.theaterService = theaterService;
    }

    @PutMapping("/{id}")
    ScreenResponse rename(@PathVariable long id, @Valid @RequestBody ScreenNameRequest request) {
        return ScreenResponse.from(theaterService.renameScreen(id, request.name()));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        theaterService.deactivateScreen(id);
    }
}
