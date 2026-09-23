package com.sumit.movieticketbookingsystem;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

/**
 * Waits for something asynchronous (an after-commit listener, a delayed payment) to have happened.
 */
public final class Eventually {

    private Eventually() {
    }

    public static void until(String what, Duration timeout, BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(timeout);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out after " + timeout + " waiting for " + what);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + what, e);
            }
        }
    }
}
