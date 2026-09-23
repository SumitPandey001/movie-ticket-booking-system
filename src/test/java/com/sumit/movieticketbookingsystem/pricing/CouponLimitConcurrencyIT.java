package com.sumit.movieticketbookingsystem.pricing;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fifty customers go for a coupon with ten uses at the same moment; exactly ten get it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CouponLimitConcurrencyIT {

    private static final int CUSTOMERS = 50;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private CouponApi coupons;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void neverMoreReservationsThanUses() throws Exception {
        String code = new CouponFixtures(mvc).coupon("""
                "discountType": "FLAT", "discountValue": 5000, "maxUses": 10, "perUserLimit": 1""");
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger reserved = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CUSTOMERS; i++) {
                pool.submit(() -> {
                    start.await();
                    try {
                        tx.executeWithoutResult(status ->
                                coupons.reserve(code, UUID.randomUUID(), UUID.randomUUID(), 5000));
                        reserved.incrementAndGet();
                    } catch (CouponInvalidException e) {
                        refused.incrementAndGet();
                    } catch (RuntimeException e) {
                        unexpected.add(e);
                    }
                    return null;
                });
            }
            start.countDown();
        }

        assertThat(unexpected).isEmpty();
        assertThat(reserved).hasValue(10);
        assertThat(refused).hasValue(CUSTOMERS - 10);
        assertThat(jdbc.sql("SELECT used_count FROM coupon WHERE code = ?").param(code).query(Integer.class).single())
                .isEqualTo(10);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM coupon_redemption r JOIN coupon c ON c.id = r.coupon_id
                        WHERE c.code = ? AND r.status = 'RESERVED'
                        """).param(code).query(Long.class).single())
                .isEqualTo(10L);
    }
}
