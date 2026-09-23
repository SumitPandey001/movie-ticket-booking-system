package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import com.sumit.movieticketbookingsystem.pricing.CouponApi;
import com.sumit.movieticketbookingsystem.pricing.CouponInvalidException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
class CouponFacade implements CouponApi {

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;

    CouponFacade(CouponRepository coupons, CouponRedemptionRepository redemptions) {
        this.coupons = coupons;
        this.redemptions = redemptions;
    }

    @Override
    public void reserve(String code, UUID userId, UUID bookingId, long discountPaise) {
        Coupon coupon = coupons.findByCode(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new CouponInvalidException("There's no coupon " + code));
        if (!redemptions.takeGlobalUse(coupon.getId())) {
            throw new CouponInvalidException("This coupon has been fully used");
        }
        if (!redemptions.takeUserUse(coupon.getId(), userId, coupon.getPerUserLimit())) {
            throw new CouponInvalidException("You've already used this coupon");
        }
        redemptions.insertReservation(coupon.getId(), userId, bookingId, discountPaise);
    }

    @Override
    public void release(UUID bookingId) {
        redemptions.releaseLive(bookingId).ifPresent(redemptions::giveBack);
    }
}
