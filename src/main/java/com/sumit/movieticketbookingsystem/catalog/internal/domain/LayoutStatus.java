package com.sumit.movieticketbookingsystem.catalog.internal.domain;

import com.sumit.movieticketbookingsystem.shared.error.IllegalTransitionException;

import java.util.EnumSet;
import java.util.Set;

public enum LayoutStatus {

    DRAFT {
        @Override
        Set<LayoutStatus> next() {
            return EnumSet.of(ACTIVE);
        }
    },
    ACTIVE {
        @Override
        Set<LayoutStatus> next() {
            return EnumSet.of(RETIRED);
        }
    },
    RETIRED;

    Set<LayoutStatus> next() {
        return EnumSet.noneOf(LayoutStatus.class);
    }

    LayoutStatus transitionTo(LayoutStatus target) {
        if (!next().contains(target)) {
            throw new IllegalTransitionException("Seat layout", this, target);
        }
        return target;
    }
}
