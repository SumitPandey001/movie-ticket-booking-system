package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.booking.internal.service.BookingQueryService;
import com.sumit.movieticketbookingsystem.booking.internal.service.CancellationService;
import com.sumit.movieticketbookingsystem.booking.internal.service.CheckoutService;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService.CreateHold;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService;
import com.sumit.movieticketbookingsystem.payment.PaymentResult;
import com.sumit.movieticketbookingsystem.shared.idempotency.Idempotent;
import com.sumit.movieticketbookingsystem.shared.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
class BookingController {

    private final HoldService holdService;
    private final CheckoutService checkoutService;
    private final CancellationService cancellationService;
    private final BookingQueryService queryService;

    BookingController(HoldService holdService, CheckoutService checkoutService,
            CancellationService cancellationService, BookingQueryService queryService) {
        this.holdService = holdService;
        this.checkoutService = checkoutService;
        this.cancellationService = cancellationService;
        this.queryService = queryService;
    }

    /** The customer's bookings, one page at a time; page starts at 0. */
    @GetMapping
    HistoryResponse history(@RequestParam BookingQueryService.View view,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size, CurrentUser user) {
        return HistoryResponse.from(queryService.history(user.id(), view, page, size));
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
    BookingResponse applyCoupon(@PathVariable UUID id, @Valid @RequestBody ApplyCouponRequest request,
            CurrentUser user) {
        return BookingResponse.from(holdService.changeCoupon(id, user.id(), request.code()));
    }

    @DeleteMapping("/{id}/coupon")
    BookingResponse removeCoupon(@PathVariable UUID id, CurrentUser user) {
        return BookingResponse.from(holdService.changeCoupon(id, user.id(), null));
    }

    /** 200 once the payment has an answer (declined included), 202 while it's still pending. */
    @PostMapping("/{id}/payments")
    @Idempotent
    ResponseEntity<CheckoutResponse> pay(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request,
            CurrentUser user) {
        CheckoutResponse response = CheckoutResponse.from(
                checkoutService.pay(id, user.id(), request.details(), request.outcome()));
        boolean pending = response.paymentStatus() == PaymentResult.Status.PENDING;
        return ResponseEntity.status(pending ? HttpStatus.ACCEPTED : HttpStatus.OK).body(response);
    }

    @PostMapping("/{id}/release")
    BookingResponse release(@PathVariable UUID id, CurrentUser user) {
        return BookingResponse.from(holdService.release(id, user.id()));
    }

    /** What cancelling now would refund. Without seatIds, every active seat. */
    @GetMapping("/{id}/refund-quote")
    RefundQuoteResponse refundQuote(@PathVariable UUID id, @RequestParam(required = false) Set<Long> seatIds,
            CurrentUser user) {
        return RefundQuoteResponse.from(cancellationService.quote(id, user.id(), orEmpty(seatIds)));
    }

    @PostMapping("/{id}/cancellations")
    @Idempotent
    CancellationResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request,
            CurrentUser user) {
        return CancellationResponse.from(cancellationService.cancel(id, user.id(), orEmpty(request.seatIds())));
    }

    /** Everything about one booking: seats, cancellations, and how the payment and any refunds stand. */
    @GetMapping("/{id}")
    BookingDetailsResponse booking(@PathVariable UUID id, CurrentUser user) {
        return BookingDetailsResponse.from(queryService.details(id, user.id()));
    }

    private static Set<Long> orEmpty(Set<Long> seatIds) {
        return seatIds == null ? Set.of() : seatIds;
    }
}
