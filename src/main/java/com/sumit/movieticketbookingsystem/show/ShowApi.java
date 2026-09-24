package com.sumit.movieticketbookingsystem.show;

import java.util.Collection;
import java.util.Map;

/**
 * What other modules may ask about a show.
 */
public interface ShowApi {

    ShowDetails show(long showId);

    Map<Long, ShowDetails> shows(Collection<Long> showIds);
}
