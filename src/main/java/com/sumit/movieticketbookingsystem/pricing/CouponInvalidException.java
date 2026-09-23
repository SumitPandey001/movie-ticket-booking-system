package com.sumit.movieticketbookingsystem.pricing;

import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;

/**
 * The coupon doesn't exist or can't be used for this order; the message says why, in words for the customer.
 */
public class CouponInvalidException extends DomainException {

    public CouponInvalidException(String reason) {
        super(ErrorCode.COUPON_INVALID, reason);
    }
}
