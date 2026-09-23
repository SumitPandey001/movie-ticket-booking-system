package com.sumit.movieticketbookingsystem.booking.internal.persistence;

import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingStatus;
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

    /** At most one, thanks to booking_one_active_hold. */
    Optional<Booking> findByUserIdAndShowIdAndStatusIn(UUID userId, long showId, Collection<BookingStatus> statuses);
}
