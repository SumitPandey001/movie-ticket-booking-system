package com.sumit.movieticketbookingsystem.show;

import java.time.Instant;

/** An admin called the show off. Everyone holding or owning seats for it gets them released and refunded. */
public record ShowCancelled(long showId, Instant cancelledAt) {
}
