package com.sumit.movieticketbookingsystem.shared.user;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps {@code app_user} in step with what the gateway tells us, so background work (notifications) can reach a
 * user after their request is long gone.
 */
@Component
public class UserDirectory {

    // ponytail: grows with every user this instance has seen and is lost on restart; swap for a bounded cache
    // if the user base gets large
    private final Map<UUID, CurrentUser> synced = new ConcurrentHashMap<>();

    private final JdbcClient jdbc;

    UserDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Only writes when this instance hasn't seen the user yet or their details changed. */
    void sync(CurrentUser user) {
        if (user.equals(synced.get(user.id()))) {
            return;
        }
        // headers are trusted but not size-checked by the gateway, hence left(...)
        jdbc.sql("""
                        INSERT INTO app_user (id, name, email, phone, role)
                        VALUES (:id, left(:name, 120), left(:email, 254), left(:phone, 20), :role)
                        ON CONFLICT (id) DO UPDATE
                        SET name = EXCLUDED.name, email = EXCLUDED.email, phone = EXCLUDED.phone,
                            role = EXCLUDED.role, updated_at = now()
                        WHERE (app_user.name, app_user.email, app_user.phone, app_user.role)
                              IS DISTINCT FROM (EXCLUDED.name, EXCLUDED.email, EXCLUDED.phone, EXCLUDED.role)
                        """)
                .param("id", user.id())
                .param("name", user.name())
                .param("email", user.email())
                .param("phone", user.phone())
                .param("role", user.role().name())
                .update();
        synced.put(user.id(), user);
    }

    /** Empty for a user who has never called the API. */
    public Optional<Recipient> recipient(UUID userId) {
        return jdbc.sql("SELECT name, email, phone FROM app_user WHERE id = ?")
                .param(userId)
                .query(Recipient.class)
                .optional();
    }
}
