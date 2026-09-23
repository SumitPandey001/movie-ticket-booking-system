package com.sumit.movieticketbookingsystem.booking.internal.job;

import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.persistence.BookingRepository;
import com.sumit.movieticketbookingsystem.inventory.InventoryApi;
import com.sumit.movieticketbookingsystem.pricing.CouponApi;
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
 * Closes holds (and unanswered payments) whose time is up, so their seats and coupons go back on sale. Reads
 * already treat a lapsed hold as free; this makes it official and puts the seats back on the counter.
 */
@Component
class HoldExpiryJob extends BatchJob<UUID> {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);
    private static final int BATCH_SIZE = 100;

    private final BookingRepository bookings;
    private final InventoryApi inventory;
    private final CouponApi coupons;
    private final Duration paymentGrace;
    private final Clock clock;

    HoldExpiryJob(TransactionTemplate tx, BookingRepository bookings, InventoryApi inventory, CouponApi coupons,
            BookingProperties properties, Clock clock) {
        super(tx);
        this.bookings = bookings;
        this.inventory = inventory;
        this.coupons = coupons;
        this.paymentGrace = properties.paymentGrace();
        this.clock = clock;
    }

    // ponytail: at most BATCH_SIZE a round, so a flood of lapsed holds drains over a few rounds
    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "holdExpiry")
    void run() {
        int expired = runOnce();
        if (expired > 0) {
            log.info("Expired {} bookings", expired);
        }
    }

    @Override
    protected List<UUID> fetchBatch() {
        Instant now = Instant.now(clock);
        return bookings.findIdsDueToExpire(now, now.minus(paymentGrace), Limit.of(BATCH_SIZE));
    }

    /**
     * Checked again here because the booking may have moved on since the batch was picked: a customer who started
     * paying has a longer hold now. A confirm that commits first makes the save fail its version check: skipped.
     */
    @Override
    protected void process(UUID bookingId) {
        Booking booking = bookings.findById(bookingId).orElseThrow();
        Instant now = Instant.now(clock);
        if (!booking.isDueToExpire(now, paymentGrace)) {
            return;
        }
        booking.expire(now);
        inventory.releaseHeld(booking.getShowId(), bookingId);
        coupons.release(bookingId);
    }
}
