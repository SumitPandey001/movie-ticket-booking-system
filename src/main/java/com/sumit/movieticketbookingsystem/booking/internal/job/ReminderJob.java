package com.sumit.movieticketbookingsystem.booking.internal.job;

import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.persistence.BookingRepository;
import com.sumit.movieticketbookingsystem.booking.internal.service.BookingEvents;
import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.job.BatchJob;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Reminds customers shortly before their show. Marking the booking and publishing ReminderDue happen in one
 * transaction, so the reminder goes into the outbox together with the mark and can't be lost or sent twice.
 * A booking confirmed inside the lead time is reminded on the next run.
 */
@Component
class ReminderJob extends BatchJob<UUID> {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private static final int BATCH_SIZE = 200;

    private final BookingRepository bookings;
    private final BookingEvents events;
    private final Duration leadTime;
    private final Clock clock;

    ReminderJob(TransactionTemplate tx, BookingRepository bookings, BookingEvents events,
            BookingProperties properties, Clock clock) {
        super(tx);
        this.bookings = bookings;
        this.events = events;
        this.leadTime = properties.reminderLeadTime();
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    @SchedulerLock(name = "showReminder")
    void run() {
        int reminded = runOnce();
        if (reminded > 0) {
            log.info("Queued reminders for {} bookings", reminded);
        }
    }

    @Override
    protected List<UUID> fetchBatch() {
        Instant now = Instant.now(clock);
        return bookings.findIdsDueForReminder(now, now.plus(leadTime), Limit.of(BATCH_SIZE));
    }

    /** Checked again: the booking may have been cancelled, or reminded by an earlier run, since it was picked. */
    @Override
    protected void process(UUID bookingId) {
        Booking booking = bookings.findById(bookingId).orElseThrow();
        Instant now = Instant.now(clock);
        if (!booking.isDueForReminder(now, leadTime)) {
            return;
        }
        booking.markReminded(now);
        events.reminderDue(booking);
    }
}
