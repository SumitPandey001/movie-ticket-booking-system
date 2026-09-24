package com.sumit.movieticketbookingsystem.notification.internal;

import com.sumit.movieticketbookingsystem.booking.BookingCancelled;
import com.sumit.movieticketbookingsystem.booking.BookingConfirmed;
import com.sumit.movieticketbookingsystem.booking.ReminderDue;
import com.sumit.movieticketbookingsystem.payment.RefundCompleted;
import com.sumit.movieticketbookingsystem.payment.RefundReason;
import com.sumit.movieticketbookingsystem.shared.Money;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Turns booking and payment events into messages. Runs after the publisher committed, so a slow mail server
 * never holds up a booking.
 *
 * No transaction on purpose: each notification_log update commits by itself, so an email that went out stays
 * SENT even if a later channel fails and the event comes round again.
 */
@Component
class NotificationListener {

    private static final DateTimeFormatter SHOW_TIME = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH);

    private final NotificationService notifications;

    NotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(BookingConfirmed booking) {
        notifications.notify(NotificationType.BOOKING_CONFIRMED, booking.bookingId(), "", booking.userId(), Map.of(
                "bookingRef", booking.bookingRef(),
                "movieTitle", booking.movieTitle(),
                "theaterName", booking.theaterName(),
                "showTime", SHOW_TIME.format(booking.showStartTime().atZone(booking.zone())),
                "seats", String.join(", ", booking.seatLabels()),
                "total", Money.ofPaise(booking.totalPaise()).inRupees()));
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(BookingCancelled cancelled) {
        notifications.notify(NotificationType.BOOKING_CANCELLED, cancelled.bookingId(),
                cancelled.cancellationId().toString(), cancelled.userId(), Map.of(
                        "bookingRef", cancelled.bookingRef(),
                        "movieTitle", cancelled.movieTitle(),
                        "seats", String.join(", ", cancelled.seatLabels()),
                        "fullyCancelled", cancelled.fullyCancelled(),
                        "refund", cancelled.refundPaise() == 0 ? ""                  // '' = nothing refunded
                                : Money.ofPaise(cancelled.refundPaise()).inRupees()));
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(ReminderDue reminder) {
        notifications.notify(NotificationType.SHOW_REMINDER, reminder.bookingId(), "", reminder.userId(), Map.of(
                "bookingRef", reminder.bookingRef(),
                "movieTitle", reminder.movieTitle(),
                "theaterName", reminder.theaterName(),
                "showTime", SHOW_TIME.format(reminder.showStartTime().atZone(reminder.zone())),
                "seats", String.join(", ", reminder.seatLabels())));
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(RefundCompleted refund) {
        notifications.notify(NotificationType.REFUND_COMPLETED, refund.bookingId(), refund.refundId().toString(),
                refund.customerId(), Map.of(
                        "bookingRef", refund.reference(),
                        "amount", Money.ofPaise(refund.amountPaise()).inRupees(),
                        "latePayment", refund.reason() == RefundReason.LATE_PAYMENT));
    }
}
