package com.sumit.movieticketbookingsystem.notification.internal;

import java.util.Locale;

enum NotificationType {

    BOOKING_CONFIRMED("Your tickets are confirmed · %s"),
    REFUND_COMPLETED("Your refund for %s is on its way");

    private final String subject;

    NotificationType(String subject) {
        this.subject = subject;
    }

    String subject(String bookingRef) {
        return subject.formatted(bookingRef);
    }

    /** Templates live at {@code templates/<channel>/<type>}, e.g. {@code email/booking-confirmed.html}. */
    String templateName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
