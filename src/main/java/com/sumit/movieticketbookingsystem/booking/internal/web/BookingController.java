package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService.CreateHold;
import com.sumit.movieticketbookingsystem.shared.user.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
class BookingController {

    private final HoldService holdService;

    BookingController(HoldService holdService) {
        this.holdService = holdService;
    }

    /** Holds seats; the response's holdExpiresAt drives the countdown in the client. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BookingResponse hold(@Valid @RequestBody HoldRequest request, CurrentUser user) {
        return BookingResponse.from(holdService.createHold(
                new CreateHold(user.id(), request.showId(), request.seatIds())));
    }

    @PostMapping("/{id}/release")
    BookingResponse release(@PathVariable UUID id, CurrentUser user) {
        return BookingResponse.from(holdService.release(id, user.id()));
    }

    @GetMapping("/{id}")
    BookingResponse booking(@PathVariable UUID id, CurrentUser user) {
        return BookingResponse.from(holdService.booking(id, user.id()));
    }
}
