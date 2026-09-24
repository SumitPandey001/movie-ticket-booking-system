package com.sumit.movieticketbookingsystem.shared.idempotency;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Every call is a single statement outside any business transaction, so a claim is committed before the request's
 * work starts and survives it being rolled back.
 */
@Repository
class IdempotencyStore {

    private final JdbcClient jdbc;

    IdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // true if the key is now ours to run; false if someone else holds it and it hasn't expired
    boolean claim(UUID userId, String key, String requestHash, Instant now, Instant expiresAt) {
        return jdbc.sql("""
                        INSERT INTO idempotency_record (user_id, idem_key, request_hash, status, created_at, expires_at)
                        VALUES (:userId, :key, :hash, 'IN_PROGRESS', :now, :expiresAt)
                        ON CONFLICT (user_id, idem_key) DO UPDATE
                            SET request_hash = EXCLUDED.request_hash, status = 'IN_PROGRESS', response_status = NULL,
                                response_body = NULL, created_at = EXCLUDED.created_at,
                                expires_at = EXCLUDED.expires_at
                            WHERE idempotency_record.expires_at < :now
                        RETURNING user_id
                        """)
                .param("userId", userId)
                .param("key", key)
                .param("hash", requestHash)
                .param("now", utc(now))
                .param("expiresAt", utc(expiresAt))
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    Optional<StoredRequest> find(UUID userId, String key) {
        return jdbc.sql("""
                        SELECT request_hash, status, response_status, response_body::text AS response_body
                        FROM idempotency_record WHERE user_id = ? AND idem_key = ?
                        """)
                .params(userId, key)
                .query((rs, row) -> new StoredRequest(rs.getString("request_hash"),
                        "COMPLETED".equals(rs.getString("status")), rs.getInt("response_status"),
                        rs.getString("response_body")))
                .optional();
    }

    void complete(UUID userId, String key, int responseStatus, String responseBody) {
        jdbc.sql("""
                        UPDATE idempotency_record
                        SET status = 'COMPLETED', response_status = ?, response_body = ?::jsonb
                        WHERE user_id = ? AND idem_key = ?
                        """)
                .params(responseStatus, responseBody, userId, key)
                .update();
    }

    void delete(UUID userId, String key) {
        jdbc.sql("DELETE FROM idempotency_record WHERE user_id = ? AND idem_key = ?").params(userId, key).update();
    }

    int deleteExpired(Instant now) {
        return jdbc.sql("DELETE FROM idempotency_record WHERE expires_at < ?").param(utc(now)).update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    record StoredRequest(String requestHash, boolean completed, int responseStatus, String responseBody) {
    }
}
