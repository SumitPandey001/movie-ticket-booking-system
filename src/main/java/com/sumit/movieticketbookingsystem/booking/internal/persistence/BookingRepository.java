package com.sumit.movieticketbookingsystem.booking.internal.persistence;

import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingStatus;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    // a booking is always loaded with its seats
    @Override
    @EntityGraph(attributePaths = "seats")
    Optional<Booking> findById(UUID id);

    /** Someone else's booking is reported as not found, so the API never confirms that it exists. */
    default Booking findOwn(UUID bookingId, UUID userId) {
        return findById(bookingId)
                .filter(booking -> booking.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Booking", bookingId));
    }

    /**
     * Like findOwn, but locks the row until the transaction ends, so two changes to the same booking
     * (a double-clicked cancel, a customer cancelling while the show is being cancelled) run one after the other.
     */
    default Booking findOwnForUpdate(UUID bookingId, UUID userId) {
        return findByIdForUpdate(bookingId)
                .filter(booking -> booking.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Booking", bookingId));
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(UUID id);

    @Query("SELECT b.id FROM Booking b WHERE b.showId = :showId AND b.status IN :statuses")
    List<UUID> findIdsByShowIdAndStatusIn(long showId, Collection<BookingStatus> statuses);

    // history pages; seats are batch-loaded (see Booking.seats), so a page doesn't cost a query per booking
    Page<Booking> findByUserIdAndStatusAndShowStartTimeAfter(UUID userId, BookingStatus status, Instant time,
            Pageable pageable);

    Page<Booking> findByUserIdAndStatusAndShowStartTimeLessThanEqual(UUID userId, BookingStatus status, Instant time,
            Pageable pageable);

    Page<Booking> findByUserIdAndStatus(UUID userId, BookingStatus status, Pageable pageable);

    /** Candidates for the hold-expiry sweeper, oldest first. */
    @Query("""
            SELECT b.id FROM Booking b
            WHERE (b.status = BookingStatus.HELD AND b.holdExpiresAt <= :now)
               OR (b.status = BookingStatus.PAYMENT_PENDING AND b.holdExpiresAt <= :graceCutoff)
            ORDER BY b.holdExpiresAt
            """)
    List<UUID> findIdsDueToExpire(Instant now, Instant graceCutoff, Limit limit);

    /** Confirmed bookings whose show starts within the lead time and that haven't been reminded yet. */
    @Query("""
            SELECT b.id FROM Booking b
            WHERE b.status = BookingStatus.CONFIRMED AND b.reminderSentAt IS NULL
              AND b.showStartTime > :now AND b.showStartTime <= :horizon
            ORDER BY b.showStartTime
            """)
    List<UUID> findIdsDueForReminder(Instant now, Instant horizon, Limit limit);

    /** At most one, thanks to booking_one_active_hold. */
    Optional<Booking> findByUserIdAndShowIdAndStatusIn(UUID userId, long showId, Collection<BookingStatus> statuses);
}
