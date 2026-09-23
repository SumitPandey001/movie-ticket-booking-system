package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.booking.internal.service.CheckoutService;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService.CreateHold;
import com.sumit.movieticketbookingsystem.shared.idempotency.Idempotent;
import com.sumit.movieticketbookingsystem.shared.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
class BookingController {

    private final HoldService holdService;
    private final CheckoutService checkoutService;

    BookingController(HoldService holdService, CheckoutService checkoutService) {
        this.holdService = holdService;
        this.checkoutService = checkoutService;
    }

    /** Holds seats; the response's holdExpiresAt drives the countdown in the client. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent
    BookingResponse hold(@Valid @RequestBody HoldRequest request, CurrentUser user) {
        return BookingResponse.from(holdService.createHold(
                new CreateHold(user.id(), request.showId(), request.seatIds(), request.couponCode())));
    }

    @PutMapping("/{id}/coupon")
    BookingResponse applyCoupon(@PathVariable UUID id, @Valid @RequestBody CouponRequest request, CurrentUser user) {
        return BookingResponse.from(holdService.changeCoupon(id, user.id(), request.code()));
    }

    @DeleteMapping("/{id}/coupon")
    BookingResponse removeCoupon(@PathVariable UUID id, CurrentUser user) {
        return BookingResponse.from(holdService.changeCoupon(id, user.id(), null));
    }

    record CouponRequest(@NotBlank @Size(max = 30) String code) {
    }

    @PostMapping("/{id}/payments")
    @Idempotent
    CheckoutResponse pay(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request, CurrentUser user) {
        return CheckoutResponse.from(checkoutService.pay(id, user.id(), request.details(), request.outcome()));
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
