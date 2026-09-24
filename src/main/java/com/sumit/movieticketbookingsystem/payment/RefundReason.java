package com.sumit.movieticketbookingsystem.payment;

public enum RefundReason {
    /** the customer cancelled seats */
    CUSTOMER,
    /** the admin cancelled the show */
    SHOW_CANCELLED,
    /** the payment went through after the seats were lost */
    LATE_PAYMENT
}
