package com.sumit.movieticketbookingsystem.show;

/**
 * What other modules may ask about a show.
 */
public interface ShowApi {

    /** @throws com.sumit.movieticketbookingsystem.shared.error.NotFoundException for an unknown id */
    ShowDetails show(long showId);
}
