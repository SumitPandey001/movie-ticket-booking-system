package com.sumit.movieticketbookingsystem.shared.idempotency;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** Drops Idempotency-Keys past their retention; an expired key is already free to reuse, this just tidies up. */
@Component
class IdempotencyCleanupJob {

    private final IdempotencyStore store;
    private final Clock clock;

    IdempotencyCleanupJob(IdempotencyStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    @SchedulerLock(name = "idempotencyCleanup")
    void run() {
        store.deleteExpired(Instant.now(clock));
    }
}
