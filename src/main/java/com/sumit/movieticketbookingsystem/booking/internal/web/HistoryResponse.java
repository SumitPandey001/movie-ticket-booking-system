package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingStatus;
import com.sumit.movieticketbookingsystem.booking.internal.service.BookingQueryService.BookingSummary;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record HistoryResponse(List<Item> items, int page, int size, long totalItems, int totalPages) {

    static HistoryResponse from(Page<BookingSummary> found) {
        return new HistoryResponse(found.map(Item::from).getContent(), found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    // seats are the ones still booked; cancelled seats are listed separately
    record Item(UUID bookingId, String bookingRef, BookingStatus status, String movieTitle, String theaterName,
                Instant showStartTime, List<String> seats, List<String> cancelledSeats, long totalPaise) {

        static Item from(BookingSummary summary) {
            Booking booking = summary.booking();
            return new Item(booking.getId(), booking.getBookingRef(), booking.getStatus(),
                    summary.show().movieTitle(), summary.show().theaterName(), booking.getShowStartTime(),
                    labels(summary.seats(), true), labels(summary.seats(), false),
                    booking.getTotals().total());
        }

        private static List<String> labels(List<BookingSeat> seats, boolean active) {
            return seats.stream().filter(seat -> seat.isActive() == active).map(BookingSeat::seatLabel).toList();
        }
    }
}
