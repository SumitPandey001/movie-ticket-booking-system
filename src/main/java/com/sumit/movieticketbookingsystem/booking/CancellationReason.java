package com.sumit.movieticketbookingsystem.booking;

public enum CancellationReason {
    CUSTOMER,
    /** The admin cancelled the show; always a full refund, fees included. */
    SHOW_CANCELLED
}
