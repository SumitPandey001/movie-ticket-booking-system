package com.sumit.movieticketbookingsystem.payment.internal;

import java.util.UUID;

/**
 * A refund row was committed and the money still has to move. Handled by RefundExecutor after commit,
 * and redelivered from the outbox if the app stops before it's done.
 */
record RefundRequested(UUID refundId) {
}
