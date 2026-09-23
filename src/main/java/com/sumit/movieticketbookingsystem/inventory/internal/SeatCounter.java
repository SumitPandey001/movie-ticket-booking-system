package com.sumit.movieticketbookingsystem.inventory.internal;

import com.sumit.movieticketbookingsystem.inventory.SeatAvailabilityReader;
import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seats-left per show, kept in Redis so the browse page doesn't count seat rows on every request.
 * Missing keys are rebuilt from Postgres. The number is for display only and may lag briefly;
 * the TTL puts a bound on any drift.
 */
@Service
class SeatCounter implements SeatAvailabilityReader {

    private static final Logger log = LoggerFactory.getLogger(SeatCounter.class);

    private final SeatInventoryRepository seats;
    private final StringRedisTemplate redis;
    private final Duration ttl;

    SeatCounter(SeatInventoryRepository seats, StringRedisTemplate redis, BookingProperties properties) {
        this.seats = seats;
        this.redis = redis;
        this.ttl = properties.cache().seatCounterTtl();
    }

    @Override
    public Map<Long, Integer> seatsLeft(Collection<Long> showIds) {
        if (showIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(showIds);
        List<String> cached;
        try {
            cached = redis.opsForValue().multiGet(ids.stream().map(SeatCounter::key).toList());
        } catch (DataAccessException e) {
            log.warn("Seat counters unavailable, counting in the database: {}", e.getMessage());
            return seats.availableCounts(ids);
        }

        Map<Long, Integer> seatsLeft = new HashMap<>();
        List<Long> missing = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String value = cached == null ? null : cached.get(i);
            if (value == null) {
                missing.add(ids.get(i));
            } else {
                seatsLeft.put(ids.get(i), Integer.parseInt(value));
            }
        }
        if (!missing.isEmpty()) {
            Map<Long, Integer> rebuilt = seats.availableCounts(missing);
            seatsLeft.putAll(rebuilt);
            store(rebuilt);
        }
        return seatsLeft;
    }

    @TransactionalEventListener
    void invalidate(SeatAvailabilityChanged change) {
        try {
            redis.delete(key(change.showId()));
        } catch (DataAccessException e) {
            log.warn("Couldn't reset the seat counter of show {}; it expires within {}: {}",
                    change.showId(), ttl, e.getMessage());
        }
    }

    private void store(Map<Long, Integer> counts) {
        try {
            counts.forEach((showId, count) -> redis.opsForValue().set(key(showId), String.valueOf(count), ttl));
        } catch (DataAccessException e) {
            log.warn("Couldn't store seat counters: {}", e.getMessage());
        }
    }

    private static String key(long showId) {
        return "seats-left:" + showId;
    }
}
