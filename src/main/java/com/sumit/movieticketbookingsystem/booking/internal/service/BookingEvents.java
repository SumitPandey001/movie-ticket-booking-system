package com.sumit.movieticketbookingsystem.booking.internal.service;

import com.sumit.movieticketbookingsystem.booking.BookingConfirmed;
import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.catalog.CatalogApi;
import com.sumit.movieticketbookingsystem.show.ShowApi;
import com.sumit.movieticketbookingsystem.show.ShowDetails;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publishes booking events with the show's details filled in, so listeners never have to call back into us.
 * Call it inside the transaction that changed the booking: the event is stored with it and delivered after commit.
 */
@Component
class BookingEvents {

    private final ShowApi shows;
    private final CatalogApi catalog;
    private final ApplicationEventPublisher events;

    BookingEvents(ShowApi shows, CatalogApi catalog, ApplicationEventPublisher events) {
        this.shows = shows;
        this.catalog = catalog;
        this.events = events;
    }

    void confirmed(Booking booking) {
        ShowDetails show = shows.show(booking.getShowId());
        events.publishEvent(new BookingConfirmed(booking.getId(), booking.getBookingRef(), booking.getUserId(),
                catalog.movie(show.movieId()).title(), theaterName(show.theaterId()), show.startTime(),
                catalog.city(show.cityId()).zone(),
                booking.getSeats().stream().map(BookingSeat::seatLabel).toList(),
                booking.getTotals().total()));
    }

    private String theaterName(long theaterId) {
        return catalog.theaters(List.of(theaterId)).get(theaterId).name();
    }
}
