package com.sumit.movieticketbookingsystem.notification.internal;

import java.util.Locale;

enum NotificationType {

    BOOKING_CONFIRMED("Your tickets are confirmed · %s"),
    BOOKING_CANCELLED("Booking %s cancelled"),
    SHOW_REMINDER("Your show starts soon · %s"),
    REFUND_COMPLETED("Your refund for %s is on its way");

    private final String subject;

    NotificationType(String subject) {
        this.subject = subject;
    }

    String subject(String bookingRef) {
        return subject.formatted(bookingRef);
    }

    /** Templates live at templates/<channel>/<type>, e.g. email/booking-confirmed.html. */
    String templateName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
