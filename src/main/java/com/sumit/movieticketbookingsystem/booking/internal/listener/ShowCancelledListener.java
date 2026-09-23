package com.sumit.movieticketbookingsystem.booking.internal.listener;

import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingStatus;
import com.sumit.movieticketbookingsystem.booking.internal.persistence.BookingRepository;
import com.sumit.movieticketbookingsystem.booking.internal.service.CancellationService;
import com.sumit.movieticketbookingsystem.show.ShowCancelled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Releases and refunds every booking of a cancelled show, each in its own transaction so one bad booking doesn't
 * undo the others. If any fail, the event is rethrown and delivered again; the ones already handled are finished
 * by then and skipped.
 */
@Component
class ShowCancelledListener {

    private static final Logger log = LoggerFactory.getLogger(ShowCancelledListener.class);

    // PAYMENT_PENDING is included so a confirm racing the cancellation is waited for (see cancelForShow)
    private static final EnumSet<BookingStatus> AFFECTED =
            EnumSet.of(BookingStatus.HELD, BookingStatus.PAYMENT_PENDING, BookingStatus.CONFIRMED);

    private final BookingRepository bookings;
    private final CancellationService cancellations;

    ShowCancelledListener(BookingRepository bookings, CancellationService cancellations) {
        this.bookings = bookings;
        this.cancellations = cancellations;
    }

    // ponytail: loads every affected booking id at once; a show has at most a few hundred bookings. Page
    // through them if screens ever get that big.
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(ShowCancelled event) {
        List<UUID> bookingIds = bookings.findIdsByShowIdAndStatusIn(event.showId(), AFFECTED);
        int failed = 0;
        for (UUID bookingId : bookingIds) {
            try {
                cancellations.cancelForShow(bookingId);
            } catch (RuntimeException e) {
                failed++;
                log.error("Couldn't cancel booking {} of cancelled show {}", bookingId, event.showId(), e);
            }
        }
        if (failed > 0) {
            throw new IllegalStateException(failed + " bookings of show " + event.showId() + " still need cancelling");
        }
    }
}
