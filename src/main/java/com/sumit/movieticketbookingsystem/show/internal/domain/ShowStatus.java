package com.sumit.movieticketbookingsystem.show.internal.domain;

import com.sumit.movieticketbookingsystem.shared.error.IllegalTransitionException;

import java.util.EnumSet;
import java.util.Set;

/**
 * "Closed for booking" isn't a status: it's worked out from the start time and the booking cutoff.
 */
public enum ShowStatus {

    SCHEDULED {
        @Override
        Set<ShowStatus> next() {
            return EnumSet.of(OPEN, CANCELLED);
        }
    },
    OPEN {
        @Override
        Set<ShowStatus> next() {
            return EnumSet.of(CANCELLED);
        }
    },
    CANCELLED;

    Set<ShowStatus> next() {
        return EnumSet.noneOf(ShowStatus.class);
    }

    ShowStatus transitionTo(ShowStatus target) {
        if (!next().contains(target)) {
            throw new IllegalTransitionException("Show", this, target);
        }
        return target;
    }
}
