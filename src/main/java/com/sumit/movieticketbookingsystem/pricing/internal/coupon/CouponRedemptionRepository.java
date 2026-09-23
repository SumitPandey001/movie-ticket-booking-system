package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * The coupon limits, enforced with conditional statements: however many customers race for the last use,
 * the database hands it to exactly one of them.
 */
@Repository
class CouponRedemptionRepository {

    private final JdbcClient jdbc;

    CouponRedemptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return false if the coupon has no uses left (or isn't active) */
    boolean takeGlobalUse(long couponId) {
        return jdbc.sql("""
                        UPDATE coupon SET used_count = used_count + 1
                        WHERE id = ? AND active AND (max_uses IS NULL OR used_count < max_uses)
                        """)
                .param(couponId)
                .update() == 1;
    }

    /** @return false if this customer has already used the coupon as often as allowed */
    boolean takeUserUse(long couponId, UUID userId, int perUserLimit) {
        return jdbc.sql("""
                        INSERT INTO coupon_user_usage (coupon_id, user_id, used_count) VALUES (:couponId, :userId, 1)
                        ON CONFLICT (coupon_id, user_id) DO UPDATE SET used_count = coupon_user_usage.used_count + 1
                        WHERE coupon_user_usage.used_count < :limit
                        """)
                .param("couponId", couponId)
                .param("userId", userId)
                .param("limit", perUserLimit)
                .update() == 1;
    }

    void insertReservation(long couponId, UUID userId, UUID bookingId, long discountPaise) {
        jdbc.sql("""
                        INSERT INTO coupon_redemption (coupon_id, user_id, booking_id, discount_paise, status)
                        VALUES (?, ?, ?, ?, 'RESERVED')
                        """)
                .params(couponId, userId, bookingId, discountPaise)
                .update();
    }

    /** Releases the booking's live redemption and returns whose use it was, so the counters can be given back. */
    Optional<Use> releaseLive(UUID bookingId) {
        return jdbc.sql("""
                        UPDATE coupon_redemption SET status = 'RELEASED', updated_at = now()
                        WHERE booking_id = ? AND status <> 'RELEASED'
                        RETURNING coupon_id, user_id
                        """)
                .param(bookingId)
                .query((rs, row) -> new Use(rs.getLong("coupon_id"), rs.getObject("user_id", UUID.class)))
                .optional();
    }

    void giveBack(Use use) {
        jdbc.sql("UPDATE coupon SET used_count = used_count - 1 WHERE id = ?").param(use.couponId()).update();
        jdbc.sql("UPDATE coupon_user_usage SET used_count = used_count - 1 WHERE coupon_id = ? AND user_id = ?")
                .params(use.couponId(), use.userId())
                .update();
    }

    int usesBy(long couponId, UUID userId) {
        return jdbc.sql("SELECT used_count FROM coupon_user_usage WHERE coupon_id = ? AND user_id = ?")
                .params(couponId, userId)
                .query(Integer.class)
                .optional()
                .orElse(0);
    }

    record Use(long couponId, UUID userId) {
    }
}
