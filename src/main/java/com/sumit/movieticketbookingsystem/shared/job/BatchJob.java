package com.sumit.movieticketbookingsystem.shared.job;

import com.sumit.movieticketbookingsystem.shared.error.IllegalTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * A job that picks a batch of items and handles each one in its own transaction, so one bad item never undoes
 * or blocks the rest. Items that changed under us (a booking confirmed just as it was about to expire) are
 * skipped quietly; anything else is logged and picked up again next round.
 */
public abstract class BatchJob<T> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final TransactionTemplate tx;

    protected BatchJob(TransactionTemplate tx) {
        this.tx = tx;
    }

    // returns how many items were handled; skipped and failed ones don't count
    public int runOnce() {
        int processed = 0;
        for (T item : fetchBatch()) {
            try {
                tx.executeWithoutResult(status -> process(item));
                processed++;
            } catch (OptimisticLockingFailureException | IllegalTransitionException e) {
                log.debug("Skipped {}: it changed while the job ran", item);
            } catch (RuntimeException e) {
                log.error("Failed on {}", item, e);
            }
        }
        return processed;
    }

    protected abstract List<T> fetchBatch();

    protected abstract void process(T item);
}
