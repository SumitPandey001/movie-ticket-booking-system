package com.sumit.movieticketbookingsystem.shared.job;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Looks after the outbox. A listener that failed (mail server down, a show cancellation that hit an error) is
 * tried again without waiting for a restart; delivered events are cleared out after a week.
 */
@Component
class EventPublicationJobs {

    // old enough that it isn't still being delivered for the first time; every listener copes with a repeat
    private static final Duration RETRY_AFTER = Duration.ofMinutes(1);
    // ponytail: a delivery that keeps failing is left alone after this many tries and needs a look (it stays in
    // event_publication); alert on those rows if that ever matters
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration KEEP_COMPLETED = Duration.ofDays(7);

    private final IncompleteEventPublications incomplete;
    private final CompletedEventPublications completed;
    private final Clock clock;

    EventPublicationJobs(IncompleteEventPublications incomplete, CompletedEventPublications completed, Clock clock) {
        this.incomplete = incomplete;
        this.completed = completed;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    @SchedulerLock(name = "eventRetry")
    void retryFailed() {
        Instant cutoff = Instant.now(clock).minus(RETRY_AFTER);
        incomplete.resubmitIncompletePublications(ResubmissionOptions.defaults()
                .withFilter(publication -> isRetryable(publication, cutoff)));
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "eventCleanup")
    void deleteOldCompleted() {
        completed.deletePublicationsOlderThan(KEEP_COMPLETED);
    }

    // The age is checked here rather than with ResubmissionOptions.withMinAge: in Modulith 2.1.1 the JDBC query
    // applies the minimum age only to legacy rows (an AND/OR precedence slip), so every FAILED row comes back.
    private static boolean isRetryable(EventPublication publication, Instant cutoff) {
        return publication.getPublicationDate().isBefore(cutoff)
                && publication.getCompletionAttempts() < MAX_ATTEMPTS;
    }
}
