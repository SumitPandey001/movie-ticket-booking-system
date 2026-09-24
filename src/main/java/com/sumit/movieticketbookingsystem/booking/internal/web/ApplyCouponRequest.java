package com.sumit.movieticketbookingsystem.booking.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ApplyCouponRequest(@NotBlank @Size(max = 30) String code) {
}
