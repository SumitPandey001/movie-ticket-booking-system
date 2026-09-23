package com.sumit.movieticketbookingsystem.shared.idempotency;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdempotencyCleanupJobIT {

    @Autowired
    private IdempotencyCleanupJob job;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void onlyExpiredKeysAreRemoved() {
        UUID user = UUID.randomUUID();
        insert(user, "expired", "now() - interval '1 minute'");
        insert(user, "live", "now() + interval '1 hour'");

        job.run();

        assertThat(jdbc.sql("SELECT idem_key FROM idempotency_record WHERE user_id = ?")
                .param(user).query(String.class).list()).containsExactly("live");
    }

    private void insert(UUID user, String key, String expiresAt) {
        jdbc.sql("""
                        INSERT INTO idempotency_record
                            (user_id, idem_key, request_hash, status, created_at, expires_at)
                        VALUES (?, ?, repeat('a', 64), 'COMPLETED', now(), %s)""".formatted(expiresAt))
                .params(user, key).update();
    }
}
