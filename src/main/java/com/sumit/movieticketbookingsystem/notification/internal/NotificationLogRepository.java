package com.sumit.movieticketbookingsystem.notification.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * One row per message we mean to send. The row is claimed before sending, so an event delivered twice finds it
 * already SENT and sends nothing.
 */
@Repository
class NotificationLogRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    NotificationLogRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * @return the row to send against, or empty if this message was already sent. A row left PENDING or FAILED
     * by an earlier attempt is taken over for the retry.
     */
    // ponytail: two deliveries of the same event at the very same moment could both claim and send twice; the
    // outbox redelivers minutes apart, so a claimed_at lease is only worth adding if that ever happens
    Optional<Long> claim(UUID bookingId, NotificationType type, String referenceId, Channel channel) {
        return jdbc.sql("""
                        INSERT INTO notification_log (booking_id, type, reference_id, channel, status, attempts)
                        VALUES (:bookingId, :type, :referenceId, :channel, 'PENDING', 1)
                        ON CONFLICT (booking_id, type, reference_id, channel) DO UPDATE
                        SET status = 'PENDING', attempts = notification_log.attempts + 1
                        WHERE notification_log.status <> 'SENT'
                        RETURNING id
                        """)
                .param("bookingId", bookingId)
                .param("type", type.name())
                .param("referenceId", referenceId)
                .param("channel", channel.name())
                .query(Long.class)
                .optional();
    }

    void markSent(long id) {
        jdbc.sql("UPDATE notification_log SET status = 'SENT', last_error = NULL, sent_at = :now WHERE id = :id")
                .param("now", Instant.now(clock).atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    void markFailed(long id, RuntimeException error) {
        jdbc.sql("UPDATE notification_log SET status = 'FAILED', last_error = left(:error, 300) WHERE id = :id")
                .param("error", error.toString())
                .param("id", id)
                .update();
    }
}
