package com.sumit.movieticketbookingsystem.show;

import java.util.Collection;
import java.util.Map;

/**
 * What other modules may ask about a show.
 */
public interface ShowApi {

    /** @throws com.sumit.movieticketbookingsystem.shared.error.NotFoundException for an unknown id */
    ShowDetails show(long showId);

    /** Batch lookup for lists; unknown ids are simply missing from the map. */
    Map<Long, ShowDetails> shows(Collection<Long> showIds);
}
