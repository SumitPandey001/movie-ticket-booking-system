package com.sumit.movieticketbookingsystem.booking.internal.persistence;

import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingStatus;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
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

    /** At most one, thanks to booking_one_active_hold. */
    Optional<Booking> findByUserIdAndShowIdAndStatusIn(UUID userId, long showId, Collection<BookingStatus> statuses);
}
