package com.sumit.movieticketbookingsystem.pricing.internal.web;

import com.sumit.movieticketbookingsystem.pricing.internal.coupon.CouponAdminService;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/coupons")
class CouponAdminController {

    private final CouponAdminService couponService;

    CouponAdminController(CouponAdminService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    List<CouponResponse> coupons() {
        return couponService.coupons().stream().map(CouponResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CouponResponse create(@Valid @RequestBody CouponRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new ValidationException("A new coupon needs a code");
        }
        return CouponResponse.from(couponService.create(request.code(), request.toTerms()));
    }

    @PutMapping("/{id}")
    CouponResponse update(@PathVariable long id, @Valid @RequestBody CouponRequest request) {
        return CouponResponse.from(couponService.update(id, request.code(), request.toTerms()));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        couponService.deactivate(id);
    }
}
